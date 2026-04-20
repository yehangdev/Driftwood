.PHONY: test package check

test:
	mvn -q test

package:
	mvn -q package

check: test
