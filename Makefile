SHELL := /bin/bash -euo pipefail
export PATH := node_modules/.bin:$(V23_ROOT)/release/go/bin:$(V23_ROOT)/roadmap/go/bin:$(V23_ROOT)/third_party/cout/node/bin:$(PATH)

# Default browserify options: use sourcemaps.
BROWSERIFY_OPTS := --debug
# Names that should not be mangled by minification.
RESERVED_NAMES := 'context,ctx,callback,cb,$$stream,serverCall'
# Don't mangle RESERVED_NAMES, and screw ie8.
MANGLE_OPTS := --mangle [ --except $(RESERVED_NAMES) --screw_ie8 ]
# Don't remove unused variables from function arguments, which could mess up
# signatures. Also don't evaulate constant expressions, since we rely on them to
# conditionally require modules only in node.
COMPRESS_OPTS := --compress [ --no-unused --no-evaluate ]
# Workaround for Browserify opening too many files: increase the limit on file
# descriptors.
# https://github.com/substack/node-browserify/issues/431
INCREASE_FILE_DESC := ulimit -S -n 2560

# If NOFIND is set, assume that files under V23_ROOT are static. This reduces
# build time dramatically.
ifdef NOFIND
	FIND := true
else
	FIND := find
endif

# Browserify and extract sourcemap, but do not minify.
define BROWSERIFY
	mkdir -p $(dir $2)
	$(INCREASE_FILE_DESC); \
	browserify $1 $(BROWSERIFY_OPTS) | exorcist $2.map > $2
endef

# Browserify, minify, and extract sourcemap.
define BROWSERIFY_MIN
	mkdir -p $(dir $2)
	$(INCREASE_FILE_DESC); \
	browserify $1 $(BROWSERIFY_OPTS) --g [ uglifyify $(MANGLE_OPTS) $(COMPRESS_OPTS) ] | exorcist $2.map > $2
endef

.DELETE_ON_ERROR:

bin: $(shell $(FIND) $(V23_ROOT) -name "*.go")
	v23 go build -a -o $@/principal v.io/x/ref/cmd/principal
	v23 go build -a -o $@/syncbased v.io/syncbase/x/ref/services/syncbase/syncbased

node_modules: package.json $(shell $(FIND) $(V23_ROOT)/roadmap/javascript/syncbase/{package.json,src})
	npm prune
	npm install
	touch $@
# Link the vanadium and syncbase modules from V23_ROOT.
	rm -rf ./node_modules/{vanadium,syncbase}
	cd "$(V23_ROOT)/release/javascript/core" && npm link
	npm link vanadium
	cd "$(V23_ROOT)/roadmap/javascript/syncbase" && npm link
	npm link syncbase
# Note, browserify 10.2.5 and up will share the vanadium module instance between
# todosapp and syncbase, since their node_modules symlinks point to a common
# location.
# https://github.com/substack/node-browserify/issues/1063
	touch node_modules

public/bundle.min.js: browser/index.js $(shell find browser) node_modules
ifdef DEBUG
	$(call BROWSERIFY,$<,$@)
else
	$(call BROWSERIFY_MIN,$<,$@)
endif

.PHONY: build
build: bin node_modules public/bundle.min.js

.PHONY: serve
serve: build
	npm start

.PHONY: clean
clean:
	rm -rf bin node_modules public/bundle.min.js

.PHONY: lint
lint:
	jshint .
