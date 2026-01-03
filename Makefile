.PHONY: test package

test:
	mvn -q test

package:
	mvn -q package
