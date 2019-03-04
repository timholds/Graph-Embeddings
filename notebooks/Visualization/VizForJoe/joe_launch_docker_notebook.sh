echo "Setting permissions..."
chmod +x joe_launch_docker_notebook.sh
chmod 777 -R "$PWD"

echo "Exporting environmental variables..."
if [[ $HOSTNAME = *"matlaber"* ]]; then
    echo "    Changing groupid to mlusers and symlinking folders"
    chown -R `id -u`:mlusers "$PWD"
fi
export UID=$(id -u)
echo "    UID=$UID"
export GID=$(id -g)
echo "    GID=$GID"
export HOSTNAME=$(hostname)
echo "    HOSTNAME=$HOSTNAME"
export DNSDOMAINNAME=$(dnsdomainname)
echo "    DNSDOMAINNAME=$DNSDOMAINNAME"

docker run --rm -p 10000:8888 -v "$PWD":/home/jovyan/work jupyter/scipy-notebook

