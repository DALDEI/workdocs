#!/bin/bash
rm -rf repo
mkdir repo
for d in core-dsl ; do 
  echo $d
  cp -pr ../$d/build/repo/* repo/
  mkdir -p repo/$d
  cp ../$d/build/publications.versions.properties repo/$d/
done
