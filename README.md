# NanoPay
A self-hostable payment processor for the [NANO cryptocurrency](https://www.nano.org). This project uses the standard
concept of 'payments' to abstract away the blockchain and function as a simple, high-level payment processor.

Once a payment is completed in NanoPay, the funds are forwarded to the configured 'storage wallet'. Any under-payments
or over-payments are automatically refunded to the sender.

By default, NanoPay uses a third-party NANO RPC node ([nanos.cc](https://api.nanos.cc/) - limited to 5000 requests/day). It
is recommended that you connect NanoPay to your own NANO node to increase service reliability and remove usage limits.

## Building From Source

### Prerequisites

In order to build this project, you are required to have & use the following tools:
- Git
- JDK 16
- Apache Maven

### Steps

First, clone the git repository by executing the following command (ensure to use the `--recursive` flag):
```bash
git clone --recursive https://github.com/Ben-D-Anderson/NanoPay
```
Next, navigate into the cloned repository and build the project as follows:
```bash
cd NanoPay && mvn clean install -Dmaven.javadoc.skip=true -Dgpg.skip
```

### Running the REST API

To run the HTTP REST API, execute the following command (where `{VERSION}` is the version of NanoPay you cloned):
```bash
$ java -jar nanopay-webapi/target/nanopay-webapi-{VERSION}.jar
```

## NanoPay as a library

The `nanopay-core` module contains the main backbone of the NanoPay payment processor - everything from wallet management
to blockchain interactions. This is ideal if you are a developer wanting to use the payment processor without the REST API.

### Brief Codebase Overview

In NanoPay, a new NANO wallet is created for each requested transaction. The design philosophy is centred around the
idea of 'alive' and 'dead' wallets, comparable to "waiting for" and "either given up waiting for, or completed"
payments, respectively.

The `WalletStorage` interface provides arbitrary storage for alive and dead wallets, with a range of default
implementations to pick from. The `WalletDeathLogger` interface provides arbitrary logging capability for when wallets
are declared dead (note: this is in addition to the default wallet death callbacks).

The `NanoPay.Builder` class follows a typical builder design pattern and is used to construct `NanoPay` instances. An
extensive example of its usage can be found in `ConfigurationParser.java`, under the `nanopay-webapi` module.

### Maven Dependency

Having built NanoPay from source, the modules will be in your local Maven repository. You can then include it as a dependency
in a Maven project by adding the following to the `dependencies` section of your `pom.xml`:
```xml
<dependency>
    <groupId>com.terraboxstudios</groupId>
    <artifactId>nanopay-core</artifactId>
    <version>{VERSION}</version>
    <scope>compile</scope>
</dependency>
```

### Database Integration ([Hibernate](https://en.wikipedia.org/wiki/Hibernate_(framework)))

If you are planning to use a database to store any NanoPay-related information, you can add the `nanopay-hibernate-storage`
module as a dependency to your project:
```xml
<dependency>
    <groupId>com.terraboxstudios</groupId>
    <artifactId>nanopay-hibernate-storage</artifactId>
    <version>{VERSION}</version>
    <scope>compile</scope>
</dependency>
```
This will provide access to the classes `HibernateWalletStorage` and `HibernateWalletDeathLogger`. Don't forget
to compile the dependencies for your database driver 