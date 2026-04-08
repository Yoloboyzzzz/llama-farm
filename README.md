# 3D Farm — Startup Guide

## Overview

3D Farm is a full-stack 3D printing farm management system for a FabLab/makerspace environment. It handles job submission, automated slicing via PrusaSlicer, print queue scheduling, real-time printer monitoring, and notifications.

| Component | Technology |
|-----------|------------|
| Backend   | Spring Boot 3.4.1 (Java 21) |
| Frontend  | Next.js 15 + React 19 + TypeScript |
| Database  | PostgreSQL |
| Slicer    | PrusaSlicer CLI (prusa-slicer-console.exe) |
| Printers  | PrusaLink and OctoPrint (REST API) |

---

## 1. Required Downloads

Install all of the following before starting.

### 1.1 Java Development Kit (JDK 21)
- **Download:** https://adoptium.net/temurin/releases/?version=21
- Choose: Windows x64, `.msi` installer
- Verify after install: `java -version` → should report `21.x.x`

### 1.2 Apache Maven
- **Download:** https://maven.apache.org/download.cgi
- Download the binary zip archive (e.g. `apache-maven-3.x.x-bin.zip`)
- Extract to a folder like `C:\Program Files\Maven`
- Add `C:\Program Files\Maven\bin` to your system `PATH`
- Verify: `mvn -version`

### 1.3 Node.js (v18 or higher)
- **Download:** https://nodejs.org/en/download (LTS version)
- Includes `npm` automatically
- Verify: `node -v` and `npm -v`

### 1.4 PostgreSQL (v12 or higher)
- **Download:** https://www.postgresql.org/download/windows/
- During install: note the `postgres` superuser password you set (you'll need it for `application.properties`)
- Default port: `5432`
- Verify: `psql -U postgres -c "\l"`

### 1.5 PrusaSlicer (v2.9.3 or higher)
- **Download:** https://github.com/prusa3d/PrusaSlicer/releases
- Install the standard Windows installer
- After install, confirm the console executable exists at:
  ```
  C:\Program Files\Prusa3D\PrusaSlicer\prusa-slicer-console.exe
  ```
- If installed elsewhere, update `slicer.prusa.executable` in `application.properties`

---

## 2. Database Setup

Open a terminal and run:

```bash
# Create the database
psql -U postgres -c "CREATE DATABASE slicerdb;"
```

The schema (all tables) is created **automatically** when the backend starts for the first time. No manual SQL needed.

---

## 3. Backend Setup

```bash
cd 3d_farm/backend

# Copy the example config and fill in your values
cp src/main/resources/application.properties.example src/main/resources/application.properties

# Download dependencies and compile
mvn clean install -DskipTests

# Start the server
mvn spring-boot:run
```

The backend starts on **http://localhost:8080**.

On first startup it will:
- Connect to PostgreSQL and create all tables
- Start the printer status poller (every 10 seconds)
- Start the print queue scheduler (every 60 seconds)

Swagger API docs are available at: http://localhost:8080/swagger-ui.html

---

## 4. Frontend Setup

```bash
cd 3d_farm/front-end

# Copy the example env file and set your backend URL
cp .env.local.example .env.local

# Install dependencies
npm install

# Start the development server
npm run dev
```

The frontend starts on **http://localhost:3000**.

For a production build:

```bash
npm run build
npm start
```

---

## 5. Configuration

### Frontend

Create `3d_farm/front-end/.env.local` from the provided example:

```bash
cp 3d_farm/front-end/.env.local.example 3d_farm/front-end/.env.local
```

| Variable | Description | Default |
|----------|-------------|---------|
| `NEXT_PUBLIC_API_URL` | URL of the Spring Boot backend | `http://localhost:8080` |

Change `NEXT_PUBLIC_API_URL` if the backend runs on a different host or port.

---

### Backend

All backend configuration lives in:

```
backend/src/main/resources/application.properties
```

An `application.properties.example` is included — copy it and fill in the values below.

### Database

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/slicerdb
spring.datasource.username=YOUR_DB_USERNAME
spring.datasource.password=YOUR_DB_PASSWORD
```

### PrusaSlicer

```properties
slicer.prusa.executable=C:/Program Files/Prusa3D/PrusaSlicer/prusa-slicer-console.exe
slicer.prusa.defaultConfig=C:/path/to/your/prusa_config.ini
slicer.output.root=C:/path/to/temp_gcode
printer.profiles.base-path=C:/path/to/3d_farm/backend/src/main/java/be/ucll/config/printerconfigs
```

- `slicer.prusa.executable` — Path to `prusa-slicer-console.exe`
- `slicer.prusa.defaultConfig` — Default `.ini` printer profile used for slicing
- `slicer.output.root` — Directory where sliced G-code files are saved. Create this folder if it doesn't exist.
- `printer.profiles.base-path` — Absolute path to the bundled printer config folder

### Label Printer (optional)

```properties
label.printer.url=http://YOUR_LABEL_PRINTER_IP:8765/api/print/namebadge
label.printer.enabled=true
```

Set `label.printer.enabled=false` if you don't have a label printer.

### Email (Gmail SMTP)

```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your@gmail.com
spring.mail.password=YOUR_GMAIL_APP_PASSWORD
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

app.base-url=https://your-domain.be
app.mail.from=your@gmail.com
```

Used for email verification on registration. Generate an App Password via Google Account → Security → App Passwords.

### JWT

```properties
jwt.secret=YOUR_BASE64_ENCODED_256BIT_SECRET
```

Generate a secret with: `openssl rand -base64 32`

---

## 6. Printer Configuration

Printer profiles (`.ini` files) are bundled at:

```
backend/src/main/java/be/ucll/config/printerconfigs/
├── MK3S/
│   └── (PLA/PETG profiles)
├── MK4/
│   └── PLA.ini
├── MK4S/
│   ├── PLA.ini
│   └── PETG.ini
├── Mini/
│   ├── PLA.ini
│   └── PETG.ini
└── CoreOne/
    └── PETG.ini
```

These are loaded into the `printer_profiles` database table. Each profile maps a printer model + material to a `.ini` file and build plate dimensions.

---

## 7. Initial App Setup (First Run)

1. Start the backend (`mvn spring-boot:run`)
2. Start the frontend (`npm run dev`)
3. Open http://localhost:3000
4. Register the first account at `/register` (a verification email will be sent)
5. Verify your email by clicking the link in the email (or set `email_verified = true` directly in the database to skip this)
6. Set that account's role to `ADMIN` directly in the database:
   ```sql
   UPDATE users SET user_role = 'ADMIN' WHERE email = 'your@email.com';
   ```
7. Log in as admin
8. Go to **Admin → Printers** and add your printers:
   - Name, IP address, API key
   - Connection type: `prusalink` or `octoprint`
   - Model (MK4, MK4S, MK3S, Mini, CoreOne), material, color
9. Use **Admin → New Job** to submit a test print

---

## 8. Application Architecture

### Backend Services

| Service | What it does |
|---------|--------------|
| `AuthService` | Login, register, password handling |
| `JwtService` | JWT token generation and validation |
| `EmailService` | Sends verification emails via Gmail SMTP |
| `UserService` | User management |
| `JobService` | Job creation, STL file storage |
| `SlicerService` | Drives PrusaSlicer CLI: STL → 3MF → G-code |
| `EstimateService` | Fast cost/time estimate without full slicing |
| `GcodeFileService` | G-code file management and queue status |
| `QueueService` | Print queue logic |
| `PrinterService` | Printer CRUD and status |
| `PrinterErrorService` | Tracks and stores printer errors |
| `MaterialService` | Material management |
| `NotificationService` | Alerts: nozzle cleaning, color changes, job done |
| `BuildPlateNester` | Packs multiple objects onto one build plate |
| `SlicingOrchestrator` | Top-level slicing workflow coordinator |

### Background Tasks

| Task | Interval | What it does |
|------|----------|--------------|
| `PrinterStatusChecker` | Every 10 seconds | Polls all printers via PrusaLink/OctoPrint API, updates status, triggers label printing on completion |
| `QueueScheduler` | Every 60 seconds | Assigns queued G-code files to available printers based on model/material/color, deadline, and print duration |

The queue scheduler uses **Europe/Brussels** timezone for deadlines:
- Prints must finish by **4:00 PM** same day, or
- Prints can run overnight and finish by **8:00 AM** next day
- Prints longer than 14 hours start immediately regardless

### Database Tables

| Table | Purpose |
|-------|---------|
| `users` | User accounts, roles (ADMIN/USER), email verification |
| `printers` | Connected printers with filament, status, and error tracking |
| `jobs` | A user's submitted batch of STL files |
| `stl_files` | Individual STL files with print settings (infill, support, brim) |
| `gcode_files` | Sliced files ready for printing, with queue position |
| `gcode_stl_files` | Join table linking G-code files to their source STL files |
| `printer_profiles` | Maps printer model + material → `.ini` config + bed size |
| `printer_errors` | Log of errors reported by printers |
| `notifications` | System alerts (nozzle cleaning, color confirmation, print complete) |

### Frontend Routes

```
/                         Landing page
/login                    Login
/register                 Registration
/verify-email             Email verification

/(protected)/user/
  dashboard               User dashboard — printer status, own jobs
  estimate                Upload STL and get time/cost estimate
  stl-svg                 STL file viewer

/(protected)/admin/
  dashboard               Full system overview
  jobs                    All jobs management
  new_job                 Create a new print job
  queue                   Print queue management
  printers                Printer management
  estimate                Batch estimation tools
  settings                System settings
  stl-svg                 STL file viewer (admin)
```

---

## 9. Key Workflows

### Submitting a Print Job
1. Upload one or more `.stl` files via **New Job**
2. Set per-file options: material, color, infill %, support, brim, copies
3. Submit → backend saves files and creates `stl_files` records
4. Admin reviews and approves → slicing begins

### Slicing Pipeline
1. STL → 3MF (via PrusaSlicer CLI + printer config)
2. Per-file settings injected (infill pattern, brim width, support type)
3. Multiple objects nested onto build plate
4. 3MF → G-code (final slice)
5. G-code metadata extracted (print time, weight, bounds)
6. G-code queued for printing

### Getting an Estimate
- Upload an STL at **Estimate** page
- Returns: estimated print time, filament weight, approximate cost
- Admin controls which users have estimate access per account

---

## 10. Troubleshooting

| Problem | Solution |
|---------|----------|
| Backend won't start | Check Java 21 is installed; check PostgreSQL is running |
| Database error on startup | Create `slicerdb` database first: `psql -U postgres -c "CREATE DATABASE slicerdb;"` |
| PrusaSlicer not found | Verify `slicer.prusa.executable` path in `application.properties` |
| Slicer output directory error | Create the folder specified in `slicer.output.root` manually |
| Printers show as offline | Verify printer IP, API key, and connection type (`prusalink` vs `octoprint`) |
| Estimate access denied | Admin must enable estimate access for the user in **Admin → Users** |
| CORS errors in browser | Add your frontend origin to `SecurityConfig.java` CORS list |
| Frontend can't reach backend | Confirm backend is on port 8080; check browser network tab for 401/403 |
| Queue not starting prints | Check printer is set to enabled and has matching model/material/color |
| Verification email not received | Check Gmail App Password config; or manually set `email_verified = true` in DB |
| JWT errors / 401 on all requests | Ensure `jwt.secret` is set in `application.properties` |

---

## License

This project is licensed under the [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License](LICENSE).
