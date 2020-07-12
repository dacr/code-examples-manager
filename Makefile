all:
	@sbt universal:packageZipTarball
	@echo "packages are generated in target/universal directory :"
	@ls target/universal/*.tgz
