-- Add version column for optimistic locking
-- This prevents race conditions when multiple workers try to update the same job
ALTER TABLE backtest_jobs 
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- Update existing rows to have version 0
UPDATE backtest_jobs SET version = 0 WHERE version IS NULL;

-- Make version column NOT NULL
ALTER TABLE backtest_jobs 
ALTER COLUMN version SET NOT NULL;
