DO
$$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'gaime_bridge') THEN
        CREATE ROLE gaime_bridge LOGIN PASSWORD 'gaime_bridge';
    END IF;
END
$$;

SELECT 'CREATE DATABASE gaime_bridge OWNER gaime_bridge'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'gaime_bridge')\gexec

GRANT ALL PRIVILEGES ON DATABASE gaime_bridge TO gaime_bridge;
