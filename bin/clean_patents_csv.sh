echo "%s/\['//g | %s/'\]//g | %s/', '/;/g | %s/\[\]/;/g | %s/ \t/\t/g | %s/\.\t/\t/g | w" | vim -e $1
