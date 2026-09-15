-- Enhanced Analysis Fields for Chess.com-like Move-by-Move Analysis
-- This migration adds support for detailed move quality classification and game summaries

-- Enhanced move_evaluations table with Chess.com-like analysis fields
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'move_evaluations' AND column_name = 'evaluation_before'
    ) THEN
        ALTER TABLE move_evaluations ADD COLUMN evaluation_before INTEGER;
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'move_evaluations' AND column_name = 'evaluation_after'
    ) THEN
        ALTER TABLE move_evaluations ADD COLUMN evaluation_after INTEGER;
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'move_evaluations' AND column_name = 'best_move'
    ) THEN
        ALTER TABLE move_evaluations ADD COLUMN best_move VARCHAR(16);
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'move_evaluations' AND column_name = 'principal_variation'
    ) THEN
        ALTER TABLE move_evaluations ADD COLUMN principal_variation TEXT;
    END IF;
    
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'move_evaluations' AND column_name = 'move_quality'
    ) THEN
        ALTER TABLE move_evaluations ADD COLUMN move_quality VARCHAR(16);
    END IF;
END $$;

-- Create game_analysis_summaries table for overall game statistics
CREATE TABLE IF NOT EXISTS game_analysis_summaries (
    summary_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id VARCHAR(128) NOT NULL REFERENCES games(game_id) ON DELETE CASCADE,
    total_moves INTEGER NOT NULL,
    accuracy DOUBLE PRECISION NOT NULL,
    best_moves INTEGER NOT NULL DEFAULT 0,
    excellent_moves INTEGER NOT NULL DEFAULT 0,
    good_moves INTEGER NOT NULL DEFAULT 0,
    book_moves INTEGER NOT NULL DEFAULT 0,
    inaccuracies INTEGER NOT NULL DEFAULT 0,
    mistakes INTEGER NOT NULL DEFAULT 0,
    blunders INTEGER NOT NULL DEFAULT 0,
    avg_centipawn_loss DOUBLE PRECISION NOT NULL,
    analysis_tier VARCHAR(16) NOT NULL,
    completed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Create analysis_jobs table for tracking analysis progress
CREATE TABLE IF NOT EXISTS analysis_jobs (
    job_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_id VARCHAR(128) NOT NULL REFERENCES games(game_id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'queued',
    tier VARCHAR(16) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    total_moves INTEGER NOT NULL DEFAULT 0,
    moves_analyzed INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- Create indexes for faster lookups
CREATE INDEX IF NOT EXISTS idx_game_analysis_summaries_game_id ON game_analysis_summaries(game_id);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_game_id ON analysis_jobs(game_id);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_status ON analysis_jobs(status);