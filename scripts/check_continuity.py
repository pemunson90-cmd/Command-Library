#!/usr/bin/env python3
"""Validate committed continuation pointers and architecture docs, never app/runtime evidence."""
import argparse,json,re,sys
from datetime import datetime
from pathlib import Path

def _valid_datetime(value):
    try:
        datetime.fromisoformat(value[:-1]+"+00:00")
        return True
    except (ValueError,TypeError):
        return False

def validate(root):
    errors=[]
    def need(condition,message):
        if not condition: errors.append(message)
    def read(path):
        p=root/path
        if not p.is_file(): errors.append("missing "+path); return ""
        return p.read_text(encoding="utf-8")
    def obj(path):
        try:return json.loads(read(path))
        except (ValueError,TypeError) as exc:errors.append("invalid JSON "+path+": "+str(exc));return {}
    def utc(value):
        return isinstance(value,str) and bool(re.fullmatch(r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z",value)) and _valid_datetime(value)
    state=obj("coordination/state.json"); lease=obj("coordination/ORCHESTRATOR.json")
    required=["schema_version","project","repository","integration_branch","checkpoint_id","verified_base_head","stage","status","next_task","next_step","first_action","active_tasks","blockers","worktree_mode","review_status","verification","last_completed","updated_utc"]
    for k in required:need(k in state,"state missing "+k)
    need(state.get("schema_version")==1,"unsupported state schema")
    need(bool(re.fullmatch(r"[0-9a-f]{40}",state.get("verified_base_head",""))),"invalid verified base SHA")
    cp=state.get("checkpoint_id","")
    need(bool(cp),"missing checkpoint ID")
    current=read("coordination/handoffs/CURRENT.md")
    current_cps=re.findall(r"^Checkpoint-ID: (.+)$",current,re.M)
    need(current_cps==[cp],"CURRENT checkpoint differs from machine state")
    task=state.get("next_task","")
    need(bool(task) and bool(state.get("first_action")),"no executable next pointer")
    need(re.findall(r"^Next task: (.+)$",current,re.M)==[task],"CURRENT next task differs")
    need(bool(read("coordination/tasks/"+task+".md")),"next task card missing")
    queue=read("coordination/QUEUE.md")
    queue_cps=re.findall(r"^Checkpoint-ID: ([^ .]+)",queue,re.M)
    need(queue_cps==[cp],"QUEUE checkpoint differs from machine state")
    queue_ids=[v for v in re.findall(r"^\|\s*([a-zA-Z0-9][a-zA-Z0-9-]*)\s*\|",queue,re.M) if v!="Task"]
    need(len(queue_ids)==len(set(queue_ids)),"QUEUE has duplicate task IDs")
    need(task in queue_ids,"next task absent from queue")
    for active in state.get("active_tasks",[]):
        need((root/("coordination/tasks/"+active+".md")).is_file(),"active card missing "+active)
    need(lease.get("status") in ["active","released"],"unknown lease status")
    need(lease.get("checkpoint_id")==cp,"lease checkpoint differs")
    need(lease.get("task_id")==task,"lease task differs from active state")
    need(utc(state.get("updated_utc")),"invalid state updated_utc (expected RFC3339 UTC)")
    need(utc(lease.get("heartbeat_utc")),"invalid lease heartbeat_utc (expected RFC3339 UTC)")
    need(utc(lease.get("expires_utc")),"invalid lease expires_utc (expected RFC3339 UTC)")
    if lease.get("status")=="active":
        need(bool(lease.get("session_id")) and bool(lease.get("expires_utc")),"active lease requires owner+expiry")
    for name in ["README.md","AGENTS.md","CLAUDE.md"]:
        need("CONTINUE.md" in read(name),name+" missing shared protocol")
    doc_cps=re.findall(r"^Checkpoint: ([^.]*)\.",read("docs/gauntlet/STATE.md"),re.M)
    need(doc_cps==[cp],"human state checkpoint differs")
    registry=obj("docs/architecture/registry.json")
    components=registry.get("components",[]); ids=[v.get("id") for v in components]
    need(len(ids)==len(set(ids))==20,"architecture must have20 unique components")
    by={v.get("id"):v for v in components}; visiting=set();done=set()
    def visit(name):
        if name in visiting:errors.append("dependency cycle "+str(name));return
        if name not in by:errors.append("unknown dependency "+str(name));return
        if name in done:return
        visiting.add(name)
        for dep in by[name].get("dependencies",[]):visit(dep)
        visiting.remove(name);done.add(name)
    paths=[]
    headings=["Purpose","COVERAGE IDs served","Public interface","Data model","Allowed dependencies","Owned paths","Shared-file change requests","Error and offline behavior","Performance budget","Tests and acceptance criteria","Step plan"]
    for v in components:
        visit(v.get("id"))
        doc=read(v.get("architecture",""))
        for h in headings:need("## "+h in doc,v.get("id","")+" missing "+h)
        for path in v.get("owned_paths",[]):
            need(path=="packages/"+v["id"]+"/**","ownership requires explicit component prefix "+path)
            paths.append((v["id"],path[:-2]))
    for i,(owner,p) in enumerate(paths):
        for other,q in paths[i+1:]:
            if owner!=other:need(not(p.startswith(q) or q.startswith(p)),"owned overlap "+owner+"/"+other)
    spec=read("docs/gauntlet/SPEC.md")
    reqs=set(re.findall(r"^\| ([A-Z]\d\d) \|",spec,re.M))
    cov=obj("docs/architecture/coverage.json").get("requirements",[])
    mapped=[v.get("requirement_id") for v in cov]
    need(len(reqs)==64 and len(mapped)==64 and set(mapped)==reqs,"architecture requirement joins differ")
    for v in cov:
        owner=v.get("primary_component")
        need(owner in by,"unknown coverage owner "+str(owner))
        need(v.get("requirement_id") in by.get(owner,{}).get("coverage_ids",[]),"owner contract omits "+str(v.get("requirement_id")))
        need(v.get("acceptance")=="AC-"+v.get("requirement_id",""),"missing named acceptance")
    parity=read("docs/gauntlet/PARITY_ACCEPTANCE.md")
    prows=re.findall(r"^\| PX\d\d-\d{3} \|",parity,re.M)
    need(len(prows)==213,"parity inventory count differs")
    for path in registry.get("shared_paths",[]):need(not path.startswith("packages/"),"shared path inside component root "+path)
    return errors

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--root",default=str(Path(__file__).resolve().parents[1]))
    args=parser.parse_args()
    errors=validate(Path(args.root))
    # Keep the Kotlin registry checks separate from the historical continuity
    # validator while running both from the established repository entry point.
    from check_kotlin_architecture import validate as validate_kotlin_architecture
    errors.extend(validate_kotlin_architecture(Path(args.root)))
    if errors:
        for e in errors:print("FAIL:",e)
        return 1
    print("PASS: continuation pointers, lease shape,20-node DAG/exclusive roots,64 requirement joins,213 parity rows")
    print("PASS: Kotlin registry matches legacy components/COVERAGE; 22-module DAG and ownership paths")
    print("This does not verify live GitHub head/lease ownership, code tests, device evidence or dependency pins.")
    return 0
if __name__=="__main__":sys.exit(main())
