FROM jupyter/scipy-notebook:610bb8b938db
USER root
COPY ./requirements.txt /home/jovyan/requirements.txt
WORKDIR /home/jovyan
RUN pip install --upgrade pip \
    && pip install -r /home/jovyan/requirements.txt
RUN conda install pytables nltk --yes
