CREATE TABLE incidents
(
    id          UUID                     PRIMARY KEY,
    title       VARCHAR(200)             NOT NULL,
    description TEXT                     NOT NULL,
    status      VARCHAR(30)              NOT NULL,
    source      VARCHAR(30)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);