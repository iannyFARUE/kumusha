# Kumusha

**Kumusha** (Shona for *home*) is a full-stack stay explorer built with Java Spring Boot and
Next.js, demonstrating MongoDB operations against the `sample_airbnb` dataset. It covers CRUD
and batch writes, aggregation reporting, geospatial proximity search, MongoDB Search and
MongoDB Vector Search using Spring Data MongoDB.

It follows the same architecture as MongoDB's `sample-app-java-mflix` sample application, ported
to a domain where listings carry coordinates, embedded reviews and rich descriptive text.

## Project Structure

```
├── README.md
├── check-requirements.sh   # Pre-flight checks for Java, Node and configuration
├── client/                 # Next.js frontend (TypeScript)
│   └── .env.example
└── server/                 # Java Spring Boot backend
    ├── src/
    ├── pom.xml
    ├── .env.example
    └── mvnw
```

## What the dataset gives you, and what it does not

`sample_airbnb.listingsAndReviews` holds roughly 5,500 listings. Three properties of this data
shape the application in ways worth knowing before you read the code:

- **`_id` values are strings**, not ObjectIds (for example `"10006546"`). There is no ObjectId
  parsing or validation anywhere in this codebase, and listings created through the API are
  given a generated string id so every document keeps the same id type.
- **Reviews are embedded** inside each listing rather than living in their own collection, so
  the review report uses `$unwind`, `$group` and `$slice` where a two-collection schema would
  use `$lookup`.
- **There are no embeddings.** Unlike `sample_mflix`, which ships an `embedded_movies`
  collection, nothing here is pre-vectorised. Vector search only works after you run the
  embedding backfill endpoint described below.

The API speaks camelCase (`propertyType`, `neighborhoodOverview`) while the documents use
snake_case with nested subdocuments (`property_type`, `address.market`, `host.host_name`). The
service layer translates between the two on every read and write, so clients never need to know
the storage layout.

## Prerequisites

- **Java 21** or higher
- **Node.js 20** or higher
- **MongoDB Atlas cluster or local deployment** with the `sample_airbnb` dataset loaded
  - [Load sample data](https://www.mongodb.com/docs/atlas/sample-data/)
  - MongoDB Search and Vector Search require Atlas. Everything else, including the geospatial
    endpoint, works against a local deployment.
- **Maven** (included via the Maven Wrapper)
- **Voyage AI API key** (only for vector search and the embedding backfill)
  - [Get a Voyage AI API key](https://www.voyageai.com/)

## Verify Requirements

Before getting started, run the verification script:

```bash
./check-requirements.sh --pre
```

This checks that Java, `JAVA_HOME` and Node are configured correctly. Run with `--help` for more
options, or without flags to also validate your `.env` file after setup.

## Getting Started

### 1. Configure the Backend

```bash
cd server
cp .env.example .env
```

Edit `.env` and set your MongoDB connection string:

```env
# MongoDB Connection
MONGODB_URI="mongodb+srv://<username>:<password>@<cluster>.mongodb.net/sample_airbnb?retryWrites=true&w=majority"

# OPTIONAL: Voyage AI Configuration (required for Vector Search)
# VOYAGE_API_KEY=your_voyage_api_key

# Write Operations (see "Read-only deployments" below)
WRITE_ENABLED=true

# Server Configuration
PORT=3001

# CORS Configuration
CORS_ORIGINS=http://localhost:3000
```

Replace `<username>`, `<password>` and `<cluster>` with your actual Atlas credentials.

## Read-only deployments

The create, update, delete and embedding-backfill endpoints have no authentication. Anyone who can
reach them can modify or delete data, and the backfill endpoint spends money against the Voyage AI
API. They are therefore **disabled unless `WRITE_ENABLED=true` is set**, and every write returns
`403` with a `WRITE_OPERATIONS_DISABLED` code while they are off.

The default is off rather than on so that a deployment which never configures the variable ends up
read-only instead of open to the internet. `server/.env.example` sets it to `true`, so the local
setup above gives you a fully writable development server.

When hosting this anywhere public, leave `WRITE_ENABLED` unset. Browsing, filtering, aggregation
reporting, proximity search and both search modes all keep working; only the mutating endpoints are
closed off.

If you later need writes on a public deployment, this flag is not the mechanism to reach for -
add real authentication instead.

### 2. Start the Backend Server

From the `server` directory:

```bash
# Using the Maven Wrapper (recommended)
./mvnw spring-boot:run

# Or on Windows
mvnw.cmd spring-boot:run
```

The server starts on `http://localhost:3001`. Verify it is running:

- API root: http://localhost:3001/
- API documentation (Swagger UI): http://localhost:3001/swagger-ui.html

On startup the application verifies the database and creates the indexes it needs: a text index
on name/summary/description, a **2dsphere index on `address.location`** (without which the
proximity endpoint fails outright), supporting indexes for filtering and reporting, and the
MongoDB Search index. Each step logs its result and none of them block startup on failure, so a
local deployment that cannot create Atlas Search indexes still runs.

### 3. Configure and Start the Frontend

In a new terminal:

```bash
cd client
cp .env.example .env.local
npm ci
npm run dev
```

The Next.js application starts on `http://localhost:3000`.

`.env.example` defaults `NEXT_PUBLIC_API_URL` to `http://localhost:3001`, which matches the
backend's default port, so no edit is needed for local development. Point it at your deployed
backend when hosting the frontend elsewhere. The `NEXT_PUBLIC_` prefix is required — Next.js only
exposes prefixed variables to the browser, and the API client runs in client components.

### 4. Access the Application

- **Frontend:** http://localhost:3000
- **Backend API:** http://localhost:3001
- **API Documentation:** http://localhost:3001/swagger-ui.html

## Enabling Vector Search

Semantic search and "find similar stays" need embeddings, which the dataset does not include.
Generate them once:

1. Add a valid `VOYAGE_API_KEY` to `server/.env` and restart the server.
2. Call the backfill endpoint, which embeds listings that do not yet have a vector:

   ```bash
   curl -X POST "http://localhost:3001/api/listings/embeddings/backfill?limit=200"
   ```

   The response reports how many listings were embedded and how many remain. The call is
   idempotent, so run it repeatedly until `remainingCount` reaches zero, or stop early and
   search across whatever subset you have embedded.

3. The vector search index is created automatically the next time the server starts and finds
   at least one embedded listing. It takes a few moments to build.

The embedding text is assembled from the listing name, property type, market, summary,
description, neighbourhood overview and amenities, so a query like *"quiet cabin near the beach
with a fireplace"* can match on more than prose alone.

**Cost note:** each backfill call sends up to 200 listings to the Voyage AI API in batches of 32.
Embedding the full collection is roughly 5,500 documents of text.

## Features

- **Browse stays:** paginated listings with filters for property type, room type, market,
  amenity, price range, capacity, review score and superhost status
- **CRUD operations:** create, read, update and delete listings individually or in batches
  through Spring Data MongoDB
- **Aggregation reporting:** three pipelines covering embedded review activity, price and rating
  statistics per property type, and the most common amenities
- **Proximity search:** `$geoNear` over the 2dsphere index, returning each result's distance
  from the query point in metres
- **Full-text search:** MongoDB Search with phrase matching on prose fields and typo-tolerant
  fuzzy matching on names, hosts and amenities, combined with compound operators
- **Semantic search:** MongoDB Vector Search over description embeddings generated with Voyage AI

## API Endpoints

All endpoints live under `/api/listings` and return a consistent envelope
(`{ success, message, data, timestamp }`).

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/listings` | List with filtering, sorting and pagination |
| GET | `/api/listings/property-types` | Distinct property types (`distinct()`) |
| GET | `/api/listings/amenities` | Distinct amenities (`distinct()` over an array field) |
| GET | `/api/listings/facets` | Property types, room types, markets and price range in one call |
| GET | `/api/listings/{id}` | Fetch one listing |
| POST | `/api/listings` | Create a listing |
| POST | `/api/listings/batch` | Create many (`insertMany`) |
| PATCH | `/api/listings/{id}` | Partial update (`updateOne` with `$set`) |
| PATCH | `/api/listings` | Update many (`updateMany`) |
| DELETE | `/api/listings/{id}` | Delete one |
| DELETE | `/api/listings` | Delete many (`deleteMany`, empty filters rejected) |
| DELETE | `/api/listings/{id}/find-and-delete` | Atomic `findOneAndDelete` |
| GET | `/api/listings/aggregations/reportingByReviews` | Recently reviewed stays (`$unwind`/`$group`/`$slice`) |
| GET | `/api/listings/aggregations/reportingByPropertyType` | Price and rating statistics (`$group`) |
| GET | `/api/listings/aggregations/reportingByAmenities` | Most common amenities (`$unwind` + `$group`) |
| GET | `/api/listings/nearby` | Proximity search (`$geoNear`) |
| GET | `/api/listings/search` | MongoDB Search |
| GET | `/api/listings/vector-search` | Semantic search |
| GET | `/api/listings/find-similar-listings` | Nearest neighbours of a listing |
| POST | `/api/listings/embeddings/backfill` | Generate description embeddings |

### Example requests

```bash
# Entire homes in Porto under $150 that sleep at least four
curl "http://localhost:3001/api/listings?market=Porto&roomType=Entire%20home/apt&maxPrice=150&minAccommodates=4"

# Stays within 2km of central Porto, nearest first
curl "http://localhost:3001/api/listings/nearby?longitude=-8.61308&latitude=41.1413&maxDistanceMeters=2000"

# Full-text search with typo tolerance on the host name
curl "http://localhost:3001/api/listings/search?summary=river%20view&host=Ana&searchOperator=should"

# Semantic search (requires embeddings)
curl "http://localhost:3001/api/listings/vector-search?q=quiet+cabin+near+the+beach+with+a+fireplace"
```

## Development

### Backend

The Java Spring Boot backend uses:

- **Spring Data MongoDB** for database operations
- **Spring Boot Web** for the REST API
- **SpringDoc OpenAPI** for API documentation
- **Maven** for dependency management

Layering follows model → repository → service (interface + implementation) → controller, with a
`config` package for cross-cutting concerns and an `exception` package with a global handler.
Simple reads go through `ListingRepository`; aggregations, batch writes and raw pipelines go
through `MongoTemplate`.

To run the tests:

```bash
cd server
./mvnw test
```

The suite covers the controller layer with `@WebMvcTest` and the service layer with Mockito,
including the camelCase-to-snake_case field translation and the numeric coercion applied to
`Decimal128` prices.

### Frontend

The Next.js frontend uses:

- **React 19** with TypeScript
- **Next.js 16** with the App Router
- **Turbopack** for fast development builds
- **CSS Modules** with no UI library

#### Development mode

```bash
cd client
npm run dev
```

#### Production build

```bash
cd client
npm run build
npm start
```

#### Linting

```bash
cd client
npm run lint
```

## Notes and limitations

- **MongoDB Search and Vector Search require Atlas.** On a local deployment the `/search`,
  `/vector-search` and `/find-similar-listings` endpoints return errors; everything else,
  including `/nearby`, works normally.
- **Prices are `Decimal128`.** They map to `BigDecimal` in Java, and aggregation pipelines
  convert them to doubles before averaging so the statistics come back as plain numbers.
- **`minimum_nights` and `maximum_nights` are strings** in the source data and are mapped as
  strings rather than coerced to numbers.
- **Embeddings are never returned to the client.** The field is excluded from list queries and
  is not mapped onto the `Listing` entity, so a 2048-dimension vector never travels with a
  listing payload.
- **Batch deletes require a non-empty filter.** An empty filter is rejected rather than treated
  as "match everything".

## License

Apache 2.0. See [LICENSE](LICENSE).
