FROM jupyter/pyspark-notebook

USER root
RUN apt update
RUN apt upgrade -y
RUN apt install -y apt-transport-https \
		   gnupg2
RUN echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list && apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823

RUN apt update

RUN apt install -y git-core \
                   scala \
                   sbt \
                   curl \
                   openjdk-8-dbg \
                   openjdk-8-jdk 
RUN echo "jovyan ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers
USER $NB_USER
WORKDIR /tmp
RUN git clone https://github.com/alexarchambault/jupyter-scala.git
WORKDIR /tmp/jupyter-scala
RUN cat jupyter-scala | sed 's/2.11.11/2.12.2/' | sed 's/0.4.2/0.4.3-SNAPSHOT/' > jupyter-scala.2
RUN mv jupyter-scala.2 jupyter-scala
#RUN rm /tmp/jupyter-scala/project/Settings.scala
#COPY ./Settings.scala /tmp/jupyter-scala/project
RUN sbt publishLocal

RUN chmod +x ./jupyter-scala
RUN ./jupyter-scala --id scala-develop --name "Scala (develop)" --force
COPY ./kernel.json /home/jovyan/.local/share/jupyter/kernels/scala-develop/
#COPY ./exp/launcher.jar /home/jovyan/.local/share/jupyter/kernels/scala-develop/

RUN mkdir /tmp/data
WORKDIR /home/jovyan/work


