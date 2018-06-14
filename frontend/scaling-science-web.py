from flask import Flask
from flask import render_template

app = Flask(__name__)


@app.route('/')
def index():
    return render_template('reusable-bubble.html')
    #return render_template('index-bubble.html')


if __name__ == '__main__':
    app.run(port=5144)
