# arxivscrape

'https://www.technologyreview.com/s/612768/we-analyzed-16625-papers-to-figure-out-where-ai-is-headed-next/'

## Files
* `arxivscraper.py`: A modified version of <a href='https://github.com/Mahdisadjadi/arxivscraper'>Mahdisadjadi/arxivscraper</a>
* `arxivscraper_getdata.ipynb`: The code for scraping metadata from the arXiv
* `articles_through_2018-11-18.csv`: The metadata for all scientific papers published to the AI section of the arXiv through November 18, 2018
* `arxivscraper_analysis.ipynb`: The code for counting all of the words and ngrams by year, and for calculating those with the greatest losses and gains

## Data columns
* `id`: arXiv paper id
* `title`: paper title
* `categories`: the arXiv categories under which the paper was filed
* `abstract`: paper abstract
* `doi`: digitial object identifier
* `created`: date of paper's upload
* `updated`: date of paper's latest update
* `authors`: paper authors last names
* `year`: year and month of paper's upload

## Source
* <a href='https://arxiv.org/'>arXiv.org</a> >
