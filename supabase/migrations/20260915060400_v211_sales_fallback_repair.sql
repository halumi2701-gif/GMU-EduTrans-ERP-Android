-- GMU EduTrans v21.1
-- Repair Sales tasks created before fallback routing was active.

create or replace function private.gmu_v211_task_route()
returns trigger
language plpgsql security definer
set search_path=public,private,pg_temp
as $$
declare target uuid; fallback uuid;
begin
  if new.status in ('DONE','CANCELLED') or new.assigned_to is not null then return new; end if;

  target:=private.gmu_v211_pick_active_user(coalesce(new.assigned_role,''));
  if target is not null then
    new.assigned_to:=target;
    return new;
  end if;

  fallback:=private.gmu_v211_pick_active_user('Manager');
  if fallback is not null then
    new.assigned_to:=fallback;
    if coalesce(new.assigned_role,'') not in ('Owner','Director','Direktur','Manager','Manager EduTrans') then
      new.assigned_role:='Manager';
    end if;
  end if;
  return new;
end;
$$;
revoke all on function private.gmu_v211_task_route() from public,anon,authenticated;

update public.automation_tasks
set assigned_role='Manager',
    assigned_to=private.gmu_v211_pick_active_user('Manager'),
    status='OPEN',
    due_at=now()+interval '4 hours',
    updated_at=now()
where task_type='SALES'
  and status='OVERDUE'
  and private.gmu_v211_role_count('Sales')=0;

select private.gmu_v211_refresh_readiness();
