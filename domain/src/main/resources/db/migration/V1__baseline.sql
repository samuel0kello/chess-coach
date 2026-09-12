create table if not exists users (
  user_id uuid primary key, email varchar(255) not null unique,
  password_hash varchar(255) not null, created_at timestamptz not null default now()
);
create table if not exists chess_accounts (
  chess_account_id uuid primary key, user_id uuid not null references users(user_id),
  chess_com_user_name varchar(64) not null, verified boolean not null default false
);
create table if not exists games (
  game_id varchar(128) primary key, chess_account_id uuid not null references chess_accounts(chess_account_id),
  pgn text not null, played_as_white boolean not null, time_class varchar(32) not null,
  opening_eco varchar(255), termination varchar(32) not null, end_time timestamptz not null
);
create table if not exists move_evaluations (
  move_evaluation_id uuid primary key, game_id varchar(128) not null references games(game_id),
  ply int not null, san_move varchar(16) not null, centipawn_loss int not null,
  quality varchar(16) not null, phase varchar(16) not null, analysis_tier varchar(16) not null
);
create table if not exists puzzles (
  puzzle_id uuid primary key, game_id varchar(128) not null references games(game_id),
  fen varchar(128) not null, theme varchar(32) not null, difficulty int not null
);
