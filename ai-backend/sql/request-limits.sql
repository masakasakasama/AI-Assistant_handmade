CREATE TABLE IF NOT EXISTS public.tatsu_home_request_counts (
  key text PRIMARY KEY,
  count bigint NOT NULL CHECK (count >= 0),
  expires_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS tatsu_home_request_counts_expiry ON public.tatsu_home_request_counts(expires_at);

CREATE OR REPLACE FUNCTION public.tatsu_home_claim(day_key text, minute_key text, daily_limit integer, minute_limit integer)
RETURNS integer[] LANGUAGE plpgsql SET search_path = pg_catalog, public AS $$
DECLARE day_count bigint; minute_count bigint;
BEGIN
  IF daily_limit < 1 OR minute_limit < 1 OR day_key = minute_key THEN
    RAISE EXCEPTION 'Invalid request limit configuration';
  END IF;
  -- Transaction-scoped lock makes admission atomic across serverless instances.
  PERFORM pg_advisory_xact_lock(74932861594021);
  DELETE FROM public.tatsu_home_request_counts WHERE key IN (
    SELECT key FROM public.tatsu_home_request_counts WHERE expires_at < now() LIMIT 100
  );
  SELECT count INTO day_count FROM public.tatsu_home_request_counts WHERE key = day_key;
  SELECT count INTO minute_count FROM public.tatsu_home_request_counts WHERE key = minute_key;
  IF coalesce(day_count, 0) >= daily_limit THEN RETURN ARRAY[0, 1]; END IF;
  IF coalesce(minute_count, 0) >= minute_limit THEN RETURN ARRAY[0, 2]; END IF;
  INSERT INTO public.tatsu_home_request_counts AS existing VALUES
    (day_key, 1, now() + interval '2 days'), (minute_key, 1, now() + interval '2 minutes')
    ON CONFLICT (key) DO UPDATE SET count = existing.count + 1;
  RETURN ARRAY[1, 0];
END;
$$;
