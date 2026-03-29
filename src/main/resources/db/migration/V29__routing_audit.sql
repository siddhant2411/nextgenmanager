CREATE TABLE routingAudit (
 id SERIAL PRIMARY KEY,
 action VARCHAR(100),
 actor VARCHAR(100),
 details VARCHAR(100),
 timestamp TIMESTAMP);
