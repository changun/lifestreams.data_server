package org.ohmage.lifestreams.utils.data_server;

import au.com.bytecode.opencsv.CSVWriter;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.mortbay.util.ByteArrayISO8859Writer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.dataformat.*;
/**
 * Created by changun on 6/19/14.
 */
class ExportRequest{
    public void setToken(String token) {
        this.token = token;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public void setRows(List<List<String>> rows) {
        this.rows = rows;
    }

    public String getToken() {
        return token;
    }

    public String getFilename()
    {
        return filename + "." + format.toLowerCase();
    }

    public String getFormat() {
        return format;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public byte[] toCSV ()throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        OutputStreamWriter byteWriter = new OutputStreamWriter(byteBuffer);
        CSVWriter writer = new CSVWriter(byteWriter, ',');
        // feed in your array (or convert your data to an array)
        writer.writeNext(headers.toArray(new String[headers.size()]));
        for(List<String> row: rows){
            writer.writeNext(row.toArray(new String[row.size()]));
        }
        writer.close();
        byteWriter.close();
        return byteBuffer.toByteArray();
    }
    public byte[] toJSON ()throws IOException {
        CsvSchema bootstrap = CsvSchema.emptySchema().withHeader();
        CsvMapper csvMapper = new CsvMapper();
        MappingIterator<Map<?, ?>> mappingIterator = csvMapper.reader(Map.class).with(bootstrap).readValues(this.toCSV());
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(mappingIterator).getBytes();
    }
    public byte[] exportBytes() throws IOException{
        if(this.getFormat().equals("JSON")){
            return toJSON();
        }else{
            return toCSV();
        }
    }
    public ExportRequest(){};

    String token, filename, format;
    List<String> headers;
    List<List<String>> rows;
}
