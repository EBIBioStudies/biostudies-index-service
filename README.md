# BioStudies Index Service

This Spring Boot service indexes and serves BioStudies submission metadata using Apache Lucene
10.3.0, with RabbitMQ for real-time updates. It supports multi-collection schemas (e.g., IDR,
ArrayExpress) and EFO ontology hierarchies

## Table of Contents

- [Features](#features)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [Testing](#testing)
- [API Documentation](#api-documentation)
- [RabbitMQ Integration](#rabbitmq-integration)
- [Project Structure](#project-structure)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)

## Overview

Processes BioStudies submissions (e.g., `{"accno": "S-BSST1432", "section": {...}}`) into searchable
Lucene indexes. Supports multi-collection schemas (IDR, ArrayExpress, BioModels, etc.), EFO ontology
hierarchies for facets/autocomplete, and real-time term counts.

## Features

- **Full-text search** with Lucene 10.3.0, facets, and analyzers per collection.
- **Real-time submission updates** via RabbitMQ STOMP over WebSocket.
- **RESTful API** for indexing, searching, and admin tasks
- **EFO ontology support**: hierarchical indexing, autocomplete, term counts.
- **Multi-collection registry** from JSON configs (fields, JSONPath extractors).
- **Kubernetes-ready** (StatefulSets, NFS for indexes)

## Technology Stack

- **Java 21**
- **Spring Boot 3.5.6**
- **Apache Lucene 10.3.0** - Full-text search and indexing
- **RabbitMQ** - Message broker for real-time updates
- **STOMP over WebSocket** - Messaging protocol
- **Maven** - Build and dependency management
- **Lombok** - Reduce boilerplate code
- **Testcontainers** - Integration testing with Docker
- **JUnit 5** - Testing framework

## Prerequisites

- Java 21 or higher
- Maven 3.6+
- Docker (optional, for integration tests and local RabbitMQ)
- RabbitMQ 3.x (if running messaging features)

## Installation

1. **Clone the repository**

## Running tests:

### Unit tests only (default)

mvn test

### Integration tests only

mvn test -P integration-tests

### Both

mvn test -P integration-tests