# Make sure to use Python2 !!!

import os
from pdfminer.pdfparser import PDFParser
from pdfminer.pdfdocument import PDFDocument
from pdfminer.pdfpage import PDFPage
from pdfminer.pdfinterp import PDFResourceManager, PDFPageInterpreter
from pdfminer.pdfdevice import PDFDevice
from pdfminer.pdfpage import PDFTextExtractionNotAllowed
from pdfminer.layout import LAParams, LTTextBox, LTTextLine
from pdfminer.converter import PDFPageAggregator

import urllib2
from cStringIO import StringIO


base_path = "./"
my_file = os.path.join(base_path + "/" + "kaashoek.pdf")
log_file = os.path.join(base_path + "/" + "pdf_log.txt")


def extract_citations(url):
        '''
        Arguments:
                url (string): url of a pdf of a research paper
        '''
        extracted_text = ""
        start_writing = False # Don't want to start writing until we hit References

        fp = open(my_file, "rb")

        print(fp)
        
        ####
        f = urllib2.urlopen(urllib2.Request(url)).read()
        fp = StringIO(f)
        ####
        
        
        parser = PDFParser(fp)
        document = PDFDocument(parser, password = "")
        if not document.is_extractable: raise PDFTextExtractionNotAllowed
        rsrcmgr = PDFResourceManager()
        laparams = LAParams()
        device = PDFPageAggregator(rsrcmgr, laparams=laparams)
        interpreter = PDFPageInterpreter(rsrcmgr, device)

        for page in PDFPage.create_pages(document):
                interpreter.process_page(page)
                layout = device.get_result()
                for lt_obj in layout:
                        if isinstance(lt_obj, LTTextBox) or isinstance(lt_obj, LTTextLine):
                                if start_writing: extracted_text += lt_obj.get_text()
                                if "References\n" in lt_obj.get_text(): start_writing = True
        fp.close()
                                
        with open(log_file, "w") as my_log:
                my_log.write(extracted_text.encode("utf-8"))
        print("Done !!")

if __name__ == "__main__":
        
        extract_citations("https://www.usenix.org/system/files/osdi18-alquraan.pdf")
