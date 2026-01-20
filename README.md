# BioStudies Index Service

A Spring Boot service for indexing and serving BioStudies search indices using Apache Lucene.
This service provides fast, full-text search capabilities for biological research submission
metadata and integrates with RabbitMQ for real-time submission updates.

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

## Features

- **Full-text search** using Apache Lucene 10.3.0
- **Real-time updates** via RabbitMQ STOMP messaging
- **RESTful API** for search and index management
- **Admin endpoints** with IP-based access control
- **Hot reload** support for development with Spring DevTools
- **Comprehensive testing** with unit and integration tests

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