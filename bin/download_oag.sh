#!/bin/bash

# OAG1
cat oag1_urls.txt | xargs -n 1 -P 12 wget
find . -name '*.zip' -print0 | xargs -0 -I {} -P 12 unzip {}
mkdir magone aminerone
mv mag* magone
mv aminer* aminerone
mv *.txt magone

# OAG2
cat oag2_urls.txt | xargs -n 1 -P 12 wget
find . -name '*.zip' -print0 | xargs -0 -I {} -P 12 unzip {}
mkdir magtwo aminertwo
mv mag* magtwo
mv aminer* aminertwo
mv *.txt magtwo




