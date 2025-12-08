CREATE USER IF NOT EXISTS 'telegraf'@'%' IDENTIFIED BY 'telegrafpass';
GRANT PROCESS, SELECT, REPLICATION CLIENT, SHOW VIEW ON *.* TO 'telegraf'@'%';
GRANT SELECT ON performance_schema.* TO 'telegraf'@'%';
GRANT SELECT ON mysql.* TO 'telegraf'@'%';
GRANT SELECT ON sys.* TO 'telegraf'@'%';
FLUSH PRIVILEGES;
