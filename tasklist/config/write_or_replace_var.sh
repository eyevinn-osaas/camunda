#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.
#

#!/bin/bash

VAR=$1
VALUE=$2
FILE=$3

grep -q "^$VAR=" $FILE || echo "$VAR=$VALUE" >> $FILE && sed -i "s|^$VAR=.*|$VAR=$VALUE|g" $FILE
