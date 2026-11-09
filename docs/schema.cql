-- Lumenote 笔记正文 Cassandra schema
-- 执行：cqlsh -f docs/schema.cql

CREATE KEYSPACE IF NOT EXISTS lumenote
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE IF NOT EXISTS lumenote.note_content (
    id      uuid PRIMARY KEY,
    content text
);
