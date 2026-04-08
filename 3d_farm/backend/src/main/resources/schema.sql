-- =====================================
-- USERS TABLE
-- =====================================
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255),
    user_role VARCHAR(20) CHECK (user_role IN ('ADMIN', 'USER'))
);

-- Email verification columns (added for email verification feature)
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_token VARCHAR(255);

-- =====================================
-- PRINTERS TABLE
-- =====================================
CREATE TABLE IF NOT EXISTS printers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    connection_type VARCHAR(50),
    ip VARCHAR(255),
    api_key VARCHAR(255),
    model VARCHAR(255),
    material VARCHAR(255),
    color VARCHAR(100),

    filament_on_spool INT,
    enough_filament BOOLEAN,

    weight_of_current_print DOUBLE PRECISION DEFAULT 0,
    status VARCHAR(50),

    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,

    enabled BOOLEAN DEFAULT TRUE,
    available_until TIMESTAMP,

    current_file_id BIGINT,
    CONSTRAINT fk_printer_current_file FOREIGN KEY (current_file_id)
        REFERENCES gcode_files (id)
        ON DELETE SET NULL
);

-- =====================================
-- JOBS TABLE
-- =====================================
CREATE TABLE IF NOT EXISTS jobs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT NOW(),
    user_id BIGINT REFERENCES users(id)
);

-- =====================================
-- STL FILES TABLE
-- =====================================
CREATE TABLE IF NOT EXISTS stl_files (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    path VARCHAR(500) NOT NULL,
    color VARCHAR(100),
    material VARCHAR(100),
    infill INT,
    brim BOOLEAN,
    support VARCHAR(50),
    instances INT DEFAULT 1,
    status VARCHAR(50) DEFAULT 'pending',

    width_mm DOUBLE PRECISION DEFAULT 0,
    depth_mm DOUBLE PRECISION DEFAULT 0,
    height_mm DOUBLE PRECISION DEFAULT 0,
    print_time_seconds INTEGER DEFAULT 0,
    min_x DOUBLE PRECISION,      -- <-- NEW FIELD
    min_y DOUBLE PRECISION,      -- <-- NEW FIELD

    job_id BIGINT NOT NULL,
    CONSTRAINT fk_stl_job FOREIGN KEY (job_id)
        REFERENCES jobs (id)
        ON DELETE CASCADE
);

-- =====================================
-- GCODE FILES TABLE
-- =====================================
CREATE TABLE IF NOT EXISTS gcode_files (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    path VARCHAR(500) NOT NULL,
    model VARCHAR(255),
    color VARCHAR(100),
    instances INT DEFAULT 1,
    duration INT,
    weight INT,
    material VARCHAR(255),
    started_at TIMESTAMP,
    queue_position INT,
    status VARCHAR(50) DEFAULT 'pending',
    label_printed BOOLEAN NOT NULL DEFAULT FALSE,

    job_id BIGINT NOT NULL,
    printer_id BIGINT,

    CONSTRAINT fk_gcode_job FOREIGN KEY (job_id)
        REFERENCES jobs (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_gcode_printer FOREIGN KEY (printer_id)
        REFERENCES printers (id)
        ON DELETE SET NULL
);

-- =====================================
-- PRINTER ERRORS TABLE
-- =====================================
CREATE TABLE IF NOT EXISTS printer_errors (
    id BIGSERIAL PRIMARY KEY,
    printer_id BIGINT NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT NOW(),
    message TEXT,
    CONSTRAINT fk_error_printer FOREIGN KEY (printer_id)
        REFERENCES printers (id) ON DELETE CASCADE
);

-- =====================================
-- GCODE ↔ STL MANY-TO-MANY JOIN TABLE
-- =====================================
CREATE TABLE IF NOT EXISTS gcode_stl_files (
    gcode_file_id BIGINT NOT NULL,
    stl_file_id   BIGINT NOT NULL,
    PRIMARY KEY (gcode_file_id, stl_file_id),
    CONSTRAINT fk_gcode_stl_gcode FOREIGN KEY (gcode_file_id)
        REFERENCES gcode_files(id) ON DELETE CASCADE,
    CONSTRAINT fk_gcode_stl_stl FOREIGN KEY (stl_file_id)
        REFERENCES stl_files(id) ON DELETE CASCADE
);

-- =====================================
-- COLUMNS ADDED AFTER INITIAL CREATION
-- =====================================
ALTER TABLE printers ADD COLUMN IF NOT EXISTS in_use BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE gcode_files ADD COLUMN IF NOT EXISTS remaining_time_seconds INT;

CREATE TABLE IF NOT EXISTS printer_profiles (
    id BIGSERIAL PRIMARY KEY,
    printer_model VARCHAR(100) NOT NULL,      -- MK4, MINI, MK3S, etc.
    material VARCHAR(100) NOT NULL,           -- PLA, PETG, TPU, ...
    config_path VARCHAR(500) NOT NULL,        -- Full path to .ini file
    plate_width_mm DOUBLE PRECISION DEFAULT 250,
    plate_depth_mm DOUBLE PRECISION DEFAULT 210,
    plate_height_mm DOUBLE PRECISION DEFAULT 180
);
