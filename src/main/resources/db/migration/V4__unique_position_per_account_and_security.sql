-- Ein Account darf pro Security nur eine aggregierte Position haben (Review-Finding: fehlender
-- Unique-Constraint erlaubte theoretisch zwei konkurrierende Positions-Datensaetze fuer dasselbe
-- (account_id, security_id)-Paar bei gleichzeitigen Requests).
CREATE UNIQUE INDEX idx_positions_account_security_unique ON positions (account_id, security_id);
