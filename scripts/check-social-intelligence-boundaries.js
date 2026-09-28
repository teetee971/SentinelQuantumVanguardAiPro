import fs from 'node:fs';
const modelPath = 'docs/SOCIAL-INTELLIGENCE-EVIDENCE-MODEL.md';
const disarmPath = 'docs/DISARM-FR-INTEGRATION.md';
for (const p of [modelPath, disarmPath]) if (!fs.existsSync(p)) throw new Error('Missing policy: ' + p);
const model = fs.readFileSync(modelPath, 'utf8');
const required = ['Source -> Observation -> Evidence -> Relationship -> DISARM mapping -> Hypothesis -> Human review -> Assessment','no actor, country, sponsor, origin-state or intent fields','Phone Core isolation invariant','unreviewed/unknown licences fail closed','cannot create `CONFIRMED_BY_ANALYST`'];
for (const marker of required) if (!model.includes(marker)) throw new Error('Missing invariant: ' + marker);
const forbidden = [/import .*PhoneCore/i,/import .*SentinelSms/i,/import .*SentinelInCall/i,/import .*CallScreen/i,/android\\.provider\\.ContactsContract/,/android\\.provider\\.CallLog/,/android\\.provider\\.Telephony/];
function walk(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>{const p=dir+'/'+e.name;return e.isDirectory()?walk(p):[p]});}
if(fs.existsSync('social-intelligence')) for(const file of walk('social-intelligence').filter(p=>/\\.(kt|java|js|ts)$/.test(p))){const body=fs.readFileSync(file,'utf8');for(const rule of forbidden)if(rule.test(body))throw new Error('Privacy boundary violation in '+file+': '+rule);}
console.log('Social Intelligence evidence/privacy boundaries: PASS');
