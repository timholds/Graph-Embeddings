FROM jupyter/scipy-notebook
USER root
COPY ./requirements.txt /home/jovyan/requirements.txt
WORKDIR /home/jovyan
RUN pip install --upgrade pip \
    && pip install -r /home/jovyan/requirements.txt
RUN conda install pytables nltk --yes
