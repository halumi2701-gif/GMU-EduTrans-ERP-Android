-- GMU EduTrans v21 Market Intelligence Autopilot
create or replace function private.gmu_v21_market_score() returns trigger language plpgsql security definer set search_path=public,private,pg_temp as $$
declare s integer:=0; chosen uuid;
begin
 s:=s + case when lower(new.region) in ('cianjur','sukabumi') then 25 else 0 end;
 s:=s + case when new.verification_status='TERVERIFIKASI' then 20 else 0 end;
 s:=s + case when coalesce(new.public_phone,'')<>'' or coalesce(new.public_email,'')<>'' then 15 else 0 end;
 s:=s + case when coalesce(new.potential_participants,0)>=100 then 20 when coalesce(new.potential_participants,0)>=40 then 12 when coalesce(new.potential_participants,0)>0 then 6 else 0 end;
 s:=s + least(20,coalesce(array_length(new.program_fit,1),0)*5);
 new.potential_score:=least(100,s);
 new.priority_grade:=case when s>=75 then 'A' when s>=50 then 'B' else 'C' end;
 if new.assigned_sales is null and new.verification_status='TERVERIFIKASI' and s>=50 then chosen:=private.gmu_v21_pick_sales(); new.assigned_sales:=chosen; end if;
 new.updated_at:=now(); return new;
end;$$;
revoke all on function private.gmu_v21_market_score() from public,anon,authenticated;
drop trigger if exists trg_v21_market_score on public.market_targets;
create trigger trg_v21_market_score before insert or update of region,verification_status,public_phone,public_email,potential_participants,program_fit,assigned_sales on public.market_targets for each row execute function private.gmu_v21_market_score();

create or replace function private.gmu_v21_market_task() returns trigger language plpgsql security definer set search_path=public,private,pg_temp as $$
begin
 if new.assigned_sales is not null and new.verification_status='TERVERIFIKASI' and new.priority_grade in ('A','B') and (tg_op='INSERT' or old.assigned_sales is distinct from new.assigned_sales or old.priority_grade is distinct from new.priority_grade) then
  insert into public.automation_tasks(task_key,assigned_to,assigned_role,task_type,title,description,due_at,priority,status,evidence_required,approval_required)
  values('v21-market:'||new.id::text,new.assigned_sales,'Sales','SALES','Prospek pasar prioritas '||new.priority_grade,new.organization_name||' • '||new.region||'. Hubungi memakai kontak publik terverifikasi, catat hasil dan next action.',now()+case when new.priority_grade='A' then interval '1 day' else interval '3 days' end,case when new.priority_grade='A' then 'HIGH' else 'NORMAL' end,'OPEN',false,false)
  on conflict(task_key) do update set assigned_to=excluded.assigned_to,due_at=least(public.automation_tasks.due_at,excluded.due_at),updated_at=now();
 end if; return new;
end;$$;
revoke all on function private.gmu_v21_market_task() from public,anon,authenticated;
drop trigger if exists trg_v21_market_task on public.market_targets;
create trigger trg_v21_market_task after insert or update of assigned_sales,priority_grade,verification_status on public.market_targets for each row execute function private.gmu_v21_market_task();

update public.market_targets set updated_at=now();