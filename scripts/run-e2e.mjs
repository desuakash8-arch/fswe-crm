import {spawn} from 'node:child_process';
import {readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import net from 'node:net';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const backend=path.join(root,'fswe/crmbackend'),frontend=path.join(root,'fswe/crmfrontend');
const backendPort=Number(process.env.CRM_E2E_BACKEND_PORT||8086),frontendPort=Number(process.env.CRM_E2E_FRONTEND_PORT||5174);
if([backendPort,frontendPort].some(p=>!Number.isInteger(p)||p<1024||p>65535)||backendPort===frontendPort)throw new Error('Choose distinct valid test ports');
const children=[];
const run=(cmd,args,cwd,options={})=>spawn(cmd,args,{cwd,windowsHide:true,stdio:'inherit',...options});
const finish=child=>new Promise((resolve,reject)=>{child.on('error',reject);child.on('exit',code=>code===0?resolve():reject(new Error(`Process exited ${code}`)));});
async function ensureFree(port){await new Promise((resolve,reject)=>{const server=net.createServer();server.once('error',()=>reject(new Error(`Port ${port} is occupied. Stop the previous test server first.`)));server.listen(port,'127.0.0.1',()=>server.close(resolve));});}
try{
  await ensureFree(backendPort);await ensureFree(frontendPort);
  const mvnArgs=['test-compile','dependency:build-classpath','-Dmdep.outputFile=target/test-classpath.txt','-Dmdep.includeScope=test'];
  const compile=process.platform==='win32'?run('cmd.exe',['/d','/s','/c','mvnw.cmd '+mvnArgs.join(' ')],backend):run('./mvnw',mvnArgs,backend);
  await finish(compile);
  const dependencies=(await readFile(path.join(backend,'target/test-classpath.txt'),'utf8')).trim();
  const cp=[path.join(backend,'target/test-classes'),path.join(backend,'target/classes'),dependencies].join(path.delimiter);
  let ready=false;let backendLog='';const server=run('java',['-cp',cp,'com.klu.crmbackend.E2eLauncher'],backend,{stdio:['ignore','pipe','pipe']});children.push(server);
  server.stdout.on('data',d=>{backendLog+=d;ready=backendLog.includes('BROWSER_TEST_READY');});server.stderr.on('data',d=>backendLog+=d);
  for(let i=0;i<120&&!ready;i++){if(server.exitCode!==null)break;await new Promise(r=>setTimeout(r,500));}
  if(!ready){await writeFile(path.join(backend,'browser-backend.log'),backendLog);throw new Error('Test backend failed; see browser-backend.log');}
  const vite=run(process.execPath,[path.join(frontend,'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port',String(frontendPort),'--strictPort'],frontend,{env:{...process.env,API_PROXY_TARGET:`http://127.0.0.1:${backendPort}`}});children.push(vite);
  let frontendReady=false;for(let i=0;i<60;i++){try{if((await fetch(`http://127.0.0.1:${frontendPort}`)).ok){frontendReady=true;break;}}catch{}await new Promise(r=>setTimeout(r,250));}
  if(!frontendReady)throw new Error('Test frontend failed to start');
  await finish(run(process.execPath,[path.join(frontend,'node_modules/@playwright/test/cli.js'),'test'],frontend));
}catch(e){console.error(e.message);process.exitCode=1;}finally{for(const child of children.reverse())child.kill();}
