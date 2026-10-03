import { readFile } from 'node:fs/promises';
const gradle=await readFile('app/build.gradle.kts','utf8');
const version=/versionName\s*=.*?\?:\s*"([^"]+)"/.exec(gradle)?.[1];
const origin=process.env.WEB_RELEASE_URL||'https://ai-assistant-handmade.vercel.app';
const expectedCommit=process.env.GITHUB_SHA||process.env.VERCEL_GIT_COMMIT_SHA;
const attempts=Number(process.env.WEB_VERIFY_ATTEMPTS||20);
for(let attempt=0;attempt<attempts;attempt++) {
  try {
    const response=await fetch(origin+'/version.json?release='+Date.now(),{cache:'no-store',signal:AbortSignal.timeout(12_000)});
    if(!response.ok)throw new Error(`version HTTP ${response.status}`);
    const deployed=await response.json();
    if(deployed.version!==version||(expectedCommit&&deployed.commit!==expectedCommit))throw new Error('Production is not on this release commit yet');
    const home=await fetch(origin+'/',{cache:'no-store',signal:AbortSignal.timeout(12_000)});
    const html=await home.text();if(!home.ok||!html.includes('TATSU HOME')||!html.includes(`id="version">${version}`))throw new Error('Production home does not match the release');
    console.log(`Verified production Web ${version} (${deployed.commit.slice(0,7)}): ${origin}`);process.exit(0);
  }catch(error){console.log(`Web release check ${attempt+1}/${attempts}: ${error.message}`);}
  if(attempt+1<attempts)await new Promise(resolve=>setTimeout(resolve,15_000));
}
throw new Error('Web production verification failed; do not publish an APK with an out-of-date Web version');
