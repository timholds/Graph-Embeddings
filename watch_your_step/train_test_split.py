#!/usr/bin/python
import numpy as np
import sys 

print(sys.argv[1])
all_data = np.load('{}/all.txt.npy'.format(sys.argv[1]))
p = np.random.permutation(len(all_data))
size_train = int(80 * len(all_data) / 100)   # Train/test split at 80 /20
train_data = all_data[p[:size_train]]
test_data = all_data[p[size_train:]]

np.save('{}/train.txt'.format(sys.argv[1]), train_data)
np.save('{}/test.txt'.format(sys.argv[1]), test_data)
np.save('{}/train_indices'.format(sys.argv[1]), p[:size_train])

all_neg_data = np.load('{}/all.neg.txt.npy'.format(sys.argv[1]))
p = np.random.permutation(len(all_neg_data))
size_train = int(80 * len(all_neg_data) / 100)   # Do you want training to be 80% ?
train_data = all_neg_data[p[:size_train]]
test_data = all_neg_data[p[size_train:]]

np.save('{}/train.neg.txt'.format(sys.argv[1]), train_data)
np.save('{}/test.neg.txt'.format(sys.argv[1]), test_data)
np.save('{}/train_neg_indices'.format(sys.argv[1]), p[:size_train])
