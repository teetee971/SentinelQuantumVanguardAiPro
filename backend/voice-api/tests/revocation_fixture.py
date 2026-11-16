"""TEST ONLY: models a provider receipt; not a production LiveKit implementation."""
class FakeMediaRevoker:
    def __init__(self, result=True):
        self.result = result
        self.calls = []

    async def revoke(self, **session):
        self.calls.append(session)
        return self.result
