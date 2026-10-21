AI-generated code. All of the changes post the original dev's were done by agy. They're simple changes

Currently, it cannot scrape reddit. If you're planning on deploying this, you must use the `FORCE_REDLIB` and pair it with a `REDLIB_INSTANCE`. This isn't 100% feature parity to the built in scraper but works well enough.

ymmv, but, recent commits targets some pr upstream for redlib. I pair this with Nachtalb/redlib's codebase.


----
# Stealth API

Multi-service content aggregator.

Build a feed and fetch data from multiple services, with a single API.

## Instances

Official instance: [https://stealth.cosmosapps.org](https://stealth.cosmosapps.org/).

## Documentation

API documentation can be found on Stealth's [homepage](https://stealth.cosmosapps.org).

OpenAPI specification is defined here: [stealth-api.yaml](/server/src/main/resources/stealth-api.yaml).

## Supported Services

- [Reddit](https://www.reddit.com/)
- [Teddit](https://teddit.net/)
- [Lemmy](https://join-lemmy.org/)

More services will be supported in the future.

## Installation

To do...

## Privacy Policy

Stealth **does not** log, collect, or store any personal information.

It does, however, connect to third party services to serve content. Your IP address will _by default_ be used to fetch content from the services supported by Stealth. This information is subject to their respective privacy policies.

## License

Copyright 2023 CosmosDev

Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html
