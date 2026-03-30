CREATE DATABASE IF NOT EXISTS learning_os CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE learning_os;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role ENUM('STUDENT','TEACHER','PARENT') NOT NULL DEFAULT 'STUDENT',
    grade VARCHAR(20),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS learning_sessions (
    id VARCHAR(64) PRIMARY KEY,
    student_id BIGINT NOT NULL,
    type ENUM('CLASS','VIDEO','HOMEWORK') NOT NULL,
    title VARCHAR(255),
    started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    ended_at DATETIME,
    duration_sec INT DEFAULT 0,
    status ENUM('ONGOING','COMPLETED') DEFAULT 'ONGOING',
    FOREIGN KEY (student_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS learning_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    student_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    timestamp_sec INT DEFAULT 0,
    payload JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES learning_sessions(id)
);

CREATE TABLE IF NOT EXISTS video_assets (
    id VARCHAR(64) PRIMARY KEY,
    student_id BIGINT NOT NULL,
    source_type VARCHAR(32) DEFAULT 'bilibili',
    source_url VARCHAR(500) NOT NULL,
    title VARCHAR(255),
    cover_url VARCHAR(500),
    duration_sec INT DEFAULT 0,
    transcript TEXT,
    outline JSON,
    status ENUM('PENDING','PROCESSING','READY','FAILED') DEFAULT 'PENDING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS homework_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64),
    student_id BIGINT NOT NULL,
    question_text TEXT NOT NULL,
    student_answer TEXT,
    hint_count INT DEFAULT 0,
    last_hint_level INT DEFAULT 0,
    result ENUM('PENDING','CORRECT','INCORRECT','ABANDONED') DEFAULT 'PENDING',
    stuck_duration_sec INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS knowledge_states (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    knowledge_key VARCHAR(200) NOT NULL,
    status ENUM('UNKNOWN','WEAK','UNSTABLE','MASTERED') DEFAULT 'UNKNOWN',
    evidence JSON,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_student_knowledge (student_id, knowledge_key),
    FOREIGN KEY (student_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS class_recordings (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    student_id BIGINT NOT NULL,
    file_path VARCHAR(500),
    transcript TEXT,
    confusion_marks JSON,
    notes TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES learning_sessions(id)
);
