CREATE DATABASE IF NOT EXISTS oiosaml;

use oiosaml;

DROP TABLE if EXISTS replay_tbl;
DROP TABLE if EXISTS assertions_tbl;
DROP TABLE if EXISTS authn_requests_tbl;
DROP TABLE if EXISTS logout_requests_tbl;

CREATE TABLE replay_tbl
(
    assertion_id VARCHAR(255) NOT NULL,
    access_time LONG NOT NULL,
    CONSTRAINT replay_assertion_id_pk PRIMARY KEY (assertion_id)
);

CREATE TABLE assertions_tbl
(
    session_id VARCHAR(255) NOT NULL,
    session_index VARCHAR(255) NOT NULL,
    assertion_id VARCHAR(255) NOT NULL,
    subject_name_id VARCHAR(255) NOT NULL,
    access_time LONG NOT NULL,
    xml_object BLOB,
    CONSTRAINT assertions_session_id_pk PRIMARY KEY (session_id),
    INDEX assertions_session_index_idx (session_index)
);

CREATE TABLE authn_requests_tbl
(
    session_id VARCHAR(255) NOT NULL,
    access_time LONG NOT NULL,
    nsis_level VARCHAR(100) NOT NULL,
    request_path VARCHAR(8000) NOT NULL,
    xml_object BLOB,
    CONSTRAINT authn_requests_session_id_pk PRIMARY KEY (session_id)
);

CREATE TABLE logout_requests_tbl
(
     session_id VARCHAR(255) NOT NULL,
     access_time LONG NOT NULL,
     xml_object BLOB,
     CONSTRAINT logout_requests_session_id_pk PRIMARY KEY (session_id)
);

