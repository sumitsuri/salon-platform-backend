# Salon Platform — Backend

Spring Boot API for the multi-tenant salon management platform.

## Tech Stack

- Java 21, Spring Boot 3.5
- Spring Security + JWT
- PostgreSQL 16, Redis 7
- OpenPDF for GST invoice PDFs

## Quick Start

### Prerequisites

- Java 21
- Maven 3.9+
- Docker (for PostgreSQL and Redis)

### Run locally

```bash
# From salon-platform root, start infrastructure
docker compose up -d

# Start API
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
mvn spring-boot:run
```

API: http://localhost:8080  
Swagger: http://localhost:8080/swagger-ui.html

## API Modules

| Module | Base Path |
|--------|-----------|
| Auth | `/api/v1/auth` |
| Branches | `/api/v1/branches` |
| Staff | `/api/v1/staff` |
| Catalog | `/api/v1/catalog` |
| Customers | `/api/v1/customers` |
| Bookings | `/api/v1/bookings` |
| Invoices | `/api/v1/invoices` |
| Analytics | `/api/v1/analytics` |
| Platform | `/api/v1/platform` |

## Demo Accounts

| Role | Email | Password |
|------|-------|----------|
| Platform Admin | platform@salonplatform.local | admin123 |
| Brand CEO | ceo@demo-brand.local | ceo123 |
| Lithos Manager | manager.lithos@demo-brand.local | manager123 |
| Webcity Manager | manager.webcity@demo-brand.local | manager123 |

## Configuration

See `src/main/resources/application.yml`. Key variables:

| Variable | Default |
|----------|---------|
| `JWT_SECRET` | (set in production) |
| PostgreSQL | localhost:5432/salon_platform |
| Redis | localhost:6379 |

## Related Repo

Frontend: [salon-platform-frontend](https://github.com/sumitkumarbharti/salon-platform-frontend)
