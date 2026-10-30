import {execFile, spawn} from 'node:child_process';
import {promisify} from 'node:util';
import {readFile,writeFile,mkdtemp,rm,mkdir} from 'node:fs/promises';
import {createReadStream} from 'node:fs';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const exec=promisify(execFile);
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const [action,...args]=process.argv.slice(2);
const option=(name,fallback)=>{const index=args.indexOf(name);return index<0?fallback:args[index+1];};
const database=option('--database','crm_db');
if(!/^[a-zA-Z][a-zA-Z0-9_]{0,63}$/.test(database))throw new Error('Invalid database name');
let local='';if(args.includes('--local'))local=await readFile(path.join(root,'fswe/crmbackend/application-local.properties'),'utf8');
function value(key,env,fallback=''){
  if(process.env[env]!==undefined)return process.env[env];
  const line=local.split(/\r?\n/).find(l=>l.startsWith(key+'='));let v=line?.slice(key.length+1)??fallback;
  const match=v.match(/^\$\{([^:}]+):(.*)\}$/);if(match)v=process.env[match[1]]??match[2];return v;
}
const bin=process.env.MYSQL_BIN||(process.platform==='win32'?'C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin':'');
const executable=name=>bin?path.join(bin,name+(process.platform==='win32'?'.exe':'')):name;
const temp=await mkdtemp(path.join(tmpdir(),'crm-db-'));
const config=path.join(temp,'client.cnf');
const quote=s=>`"${s.replaceAll('\\','\\\\').replaceAll('"','\\"').replaceAll('\n','\\n')}"`;
try {
  await writeFile(config,`[client]\nhost=${quote(process.env.DB_HOST||'127.0.0.1')}\nport=${process.env.DB_PORT||'3306'}\nuser=${quote(value('spring.datasource.username','DB_USERNAME','root'))}\npassword=${quote(value('spring.datasource.password','DB_PASSWORD'))}\n`,{mode:0o600});
  const defaults=`--defaults-extra-file=${config}`;
  if(action==='backup'){
    const output=path.resolve(option('--output',path.join(root,'backups',`${database}-${new Date().toISOString().replaceAll(':','-')}.sql`)));
    await mkdir(path.dirname(output),{recursive:true});
    await exec(executable('mysqldump'),[defaults,'--single-transaction','--no-tablespaces','--routines','--triggers',`--result-file=${output}`,database]);
    console.log(`Backup saved: ${output}`);
  }else if(action==='restore'){
    const input=option('--input');if(!input)throw new Error('--input backup.sql is required');
    const exists=await exec(executable('mysql'),[defaults,'--batch','--skip-column-names','-e',`SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='${database}'`]);
    if(exists.stdout.trim())throw new Error('Restore requires a NEW database name; existing databases are never overwritten');
    await exec(executable('mysql'),[defaults,'-e',`CREATE DATABASE \`${database}\``]);
    await new Promise((resolve,reject)=>{
      const child=spawn(executable('mysql'),[defaults,database],{stdio:['pipe','ignore','pipe'],windowsHide:true});let error='';child.stderr.on('data',d=>error+=d);
      const source=createReadStream(path.resolve(input));source.on('error',reject);source.pipe(child.stdin);child.on('error',reject);child.on('exit',code=>code===0?resolve():reject(new Error(error)));
    });
    console.log(`Backup restored to new database: ${database}`);
  }else if(action==='counts'){
    const tables=['users','companies','customers','leads','deals','interactions','tasks','notifications'];
    const sql=tables.map(t=>`SELECT '${t}' AS table_name,COUNT(*) AS records FROM \`${t}\``).join(' UNION ALL ');
    const result=await exec(executable('mysql'),[defaults,'--batch',database,'-e',sql]);console.log(result.stdout);
  }else throw new Error('Usage: node scripts/database.mjs backup|restore|counts --local [--database name] [--output file | --input file]');
}finally{
  const resolved=path.resolve(temp);
  if(path.dirname(resolved)!==path.resolve(tmpdir()) || !path.basename(resolved).startsWith('crm-db-'))throw new Error('Unexpected temporary directory');
  await rm(resolved,{recursive:true,force:true});
}
