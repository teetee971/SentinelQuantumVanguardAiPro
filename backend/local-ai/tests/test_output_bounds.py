"""Real child-process fixtures, not model inference or canned production answers."""
import asyncio
import importlib.util
from pathlib import Path
import sys
import pytest
from fastapi import HTTPException

spec = importlib.util.spec_from_file_location('bounded_service', Path(__file__).parents[1] / 'service.py')
service = importlib.util.module_from_spec(spec)
spec.loader.exec_module(service)


@pytest.mark.parametrize('fd', [1, 2])
def test_generation_rejects_output_overflow_before_child_exits(tmp_path, fd):
    async def run():
        pidfile = tmp_path / 'pid'
        binary = tmp_path / 'flood'
        binary.write_text(f'#!{sys.executable}\nimport os,time\n'
                          f'open({str(pidfile)!r},"w").write(str(os.getpid()))\n'
                          f'os.write({fd},b"x"*200000)\ntime.sleep(60)\n')
        binary.chmod(0o700)
        runtime = object.__new__(service.LlamaRuntime)
        runtime.binary = binary; runtime.model = tmp_path / 'unused'
        runtime.busy = asyncio.Lock()
        try:
            with pytest.raises(HTTPException) as exc:
                await asyncio.wait_for(runtime.generate('fixture'), 2)
            assert exc.value.status_code == 502
        finally:
            assert not runtime.busy.locked()
            if pidfile.exists():
                assert not Path('/proc/' + pidfile.read_text()).exists(), 'Overflow child not reaped'
    asyncio.run(run())


@pytest.mark.parametrize('reason', ['timeout', 'cancel', 'double_cancel'])
def test_bounded_reader_reaps_child_on_interruption(reason):
    async def run():
        process = await asyncio.create_subprocess_exec(sys.executable, '-c',
            'import time; time.sleep(60)', stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        try:
            task = asyncio.create_task(service._communicate_bounded(process, .05 if reason == 'timeout' else 30))
            if reason != 'timeout':
                await asyncio.sleep(.02)
                task.cancel()
                if reason == 'double_cancel':
                    asyncio.get_running_loop().call_soon(task.cancel)
            with pytest.raises(asyncio.TimeoutError if reason == 'timeout' else asyncio.CancelledError):
                await task
            assert process.returncode is not None
            assert not Path(f'/proc/{process.pid}').exists()
        finally:
            if process.returncode is None:
                process.kill(); await process.wait()
    asyncio.run(run())


def test_bounded_reader_drains_both_pipes_without_deadlock():
    async def run():
        process = await asyncio.create_subprocess_exec(sys.executable, '-c',
            'import os; os.write(1,b"a"*8192); os.write(2,b"b"*65536)',
            stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        try:
            stdout, stderr = await service._communicate_bounded(process, 2)
            assert stdout == b'a'*8192
            assert stderr == b'b'*65536
            assert process.returncode == 0
        finally:
            if process.returncode is None:
                process.kill(); await process.wait()
    asyncio.run(run())
