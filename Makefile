ifeq ($(origin NETLOGO), undefined)
  NETLOGO=../..
endif

ifeq ($(origin SCALA_HOME), undefined)
  SCALA_HOME=../..
endif

SRCS=$(wildcard src/main/scala/*.scala)

hubnet-web.jar: $(SRCS) manifests/web.txt Makefile
	./bin/sbt update compile
	jar cmf manifests/web.txt hubnet-web.jar -C target/scala_2.9.1/classes/ .
	cp lib_managed/scala_2.9.1/compile/*.jar .
