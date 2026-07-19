-- Kill switch for issued tokens (sessions + personal access tokens): each
-- token embeds the account's token_version at mint time, and the access gate
-- rejects any token whose value is behind the account's current one. Bumping
-- this column logs the account out everywhere.
ALTER TABLE users ADD COLUMN token_version integer NOT NULL DEFAULT 0;
