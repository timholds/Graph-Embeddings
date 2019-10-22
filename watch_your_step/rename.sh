#!/bin/bash 
# To run for non-default directory, pass the path of the directory that has training data inside,
# where the training directory should be filled with year directories, with each year dir looking nearly identical (except with different data)
if [ -z "$1" ]; then # no argumment passed, run for default directory
  DBASE=/mnt/raid/data/features/top84/embeddings/optimized
  DIR=$DBASE/training_data/*
  echo "Renaming data in $DBASE/training_data"
  echo "To run for another directory, pass an argument with the directory that contains the edgelists for all years"
  for d in $DIR; do
    echo "Moving data for $d" && \
    mv $d/train.txt.npy $d/all.txt.npy && \
    mv $d/train.neg.txt.npy $d/all.neg.txt.npy 
  done
  echo "Finished moving data in $DBASE/training_data"
else
  DBASE=$1
  DIR=$DBASE/training_data/*
  echo "Renaming data in $DBASE/training_data"
  for d in $DIR; do
    echo "Moving data in for $d" && \
    mv $d/train.txt.npy $d/all.txt.npy && \
    mv $d/train.neg.txt.npy $d/all.neg.txt.npy 
  done
  echo "Finished moving data in  $DBASE/training_data"

fi

