CREATE TABLE CUSTOM_API_KEY
(
    ID bigserial not null primary key constraint custom_api_key_pk,
    KEY varchar(256) not null unique,
    SECRET varchar(256) not null,
    STATUS varchar(60) not null,
    OWNER varchar(120) not null,
    CREATED timestamp(6),
    EXPIRED timestamp(6),
    PERMANENT char(1)  not null default 'Y'
)