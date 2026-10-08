-- Persistent hourly request limits for the public preview and signed-in assistant.
create table private.ai_assistant_request_limits (
  subject_hash text primary key check (subject_hash ~ '^[0-9a-f]{64}$'),
  window_started_at timestamptz not null,
  request_count integer not null check (request_count > 0)
);

revoke all on table private.ai_assistant_request_limits from public, anon, authenticated;

create function public.consume_ai_assistant_request(p_subject_hash text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_now timestamptz := pg_catalog.clock_timestamp();
  v_count integer;
begin
  if p_subject_hash !~ '^[0-9a-f]{64}$' then
    raise sqlstate 'PT400' using message = 'Invalid rate-limit subject.';
  end if;

  delete from private.ai_assistant_request_limits
  where window_started_at < v_now - interval '1 day';

  insert into private.ai_assistant_request_limits(subject_hash, window_started_at, request_count)
  values (p_subject_hash, v_now, 1)
  on conflict (subject_hash) do update
  set window_started_at = case
        when private.ai_assistant_request_limits.window_started_at <= v_now - interval '1 hour' then v_now
        else private.ai_assistant_request_limits.window_started_at
      end,
      request_count = case
        when private.ai_assistant_request_limits.window_started_at <= v_now - interval '1 hour' then 1
        else private.ai_assistant_request_limits.request_count + 1
      end
  returning request_count into v_count;

  return v_count <= 10;
end;
$$;

revoke all on function public.consume_ai_assistant_request(text) from public, anon, authenticated;
grant execute on function public.consume_ai_assistant_request(text) to service_role;
