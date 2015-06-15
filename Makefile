SHELL := /bin/bash -euo pipefail
PATH := node_modules/.bin:$(PATH)

define BROWSERIFY
	mkdir -p $(dir $2)
	browserify $1 -d -o $2
endef

define BROWSERIFY_MIN
	mkdir -p $(dir $2)
	browserify $1 -d -p [minifyify --map $(notdir $2).map --output $2.map] -o $2
endef

.DELETE_ON_ERROR:

node_modules: package.json
	npm prune
	npm install
	touch $@

public/bundle.min.js: browser/index.js $(shell find browser) node_modules
ifdef DEBUG
	$(call BROWSERIFY,$<,$@)
else
	$(call BROWSERIFY_MIN,$<,$@)
endif

.PHONY: build
build: public/bundle.min.js node_modules

.PHONY: serve
serve: build
	npm start

.PHONY: clean
clean:
	rm -rf node_modules public/bundle.min.js

.PHONY: lint
lint:
	jshint .
