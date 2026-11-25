-- V2 (MySQL): roles and token versions for the users table.
--
-- Table <-> entity:  users <-> entity/User (new fields role and tokenVersion; entity/Role is the enum).
-- Never edit a script that has been applied; the matching copy for the automated tests is in db/migration/h2.
--
--   * role           USER (everyone who registers) or ADMIN (can manage other accounts: /api/admin/users). Existing accounts
--                    become USER. The first administrator is made with the INITIAL_ADMIN setting or the demo data.
--   * token_version  Counts how many times this account's login tokens were revoked. Every token carries the number it was
--                    issued with (security/JwtService); security/JwtAuthenticationFilter refuses a token whose number is no
--                    longer the account's. It goes up when the password is changed, the role changes or the account is
--                    disabled, so those actions end every session at once. Existing tokens (no number) count as 0.

ALTER TABLE users ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;
