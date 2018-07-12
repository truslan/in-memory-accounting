Accounting
==========

Simple in memory near lock-free app to track money transfers between accounts with embedded web server ([Jetty](https://www.eclipse.org/jetty/)).

To build project use:
`mvn clean pakcage`

To run demo server use:
`java -jar billing-1.0-SNAPSHOT-jar-with-dependencies.jar server -p 9999`

To run demo transfer use:
`java -jar billing-1.0-SNAPSHOT-jar-with-dependencies.jar demo -a 100 -c 4 -n 10000 -u http://localhost:9999/`

Usage: java -jar billing-1.0-SNAPSHOT-jar-with-dependencies.jar [command] [command options]

		Options:
			-h, --help
				Show this help
				Default: false
		Commands:
			server
				Usage: server [options]
					Options:
					* -p, --port
							Server port
							Default: 8080
	
			demo
				Usage: demo [options]
					Options:
						-a, --accounts
							Number of total created accounts
							Default: 100
						-c, --concurrency
							Number of concurrent requests
							Default: 8
						-n, --number
							Number of total transfer requests
							Default: 1000
						-u, --url
							Server url
							Default: http://localhost:8080/