#!/bin/bash
set -x

PATH="/bin:/usr/bin:/sbin:/usr/sbin:/usr/local/bin:/usr/local/sbin"
BASENAME="${0##*/}"

echo "Program Executable Jar: '$program_executable'"

java -jar $program_executable