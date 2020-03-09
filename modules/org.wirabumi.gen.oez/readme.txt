::Easy to use openbravo, initial data load, and fast configuration. This module contain features bellow:
1. configuration wizard for easy configuration after initial organization setup.
2. preconfiguration data import, including: business partner, partner balance, product, price, inventory, and average cost. Also, excel template for data import available.

::before install:
1. merge org folder inside <openbravo home>/modules/org.wirabumi.gen.oez/srcClient into <openbravo home>/src
2. merge src folder insite <openbravo home>/modules/org.wirabumi.gen.oez/srcClientApplication into <openbravo home>/modules/org.openbravo.client.application

::ant compile sequences:
compile: ant update.database
compile: ant apply.modules
compile: ant complie.complete.deploy

after install:
1. jika ingin mengaktifkan report "advance movement report", maka run sql script oez_advproductmovementreport.sql in folder "resources/07. sql advance movement report"
2. jika ingin mengaktifkan docment sequence per organisasi, maka jalankan:
2.1 sql script ad_sequence_doc.sql in folder "resources/09. sql doc sequence per organization"
2.2 sql script ad_sequence_doctype.sql in folder "resources/09. sql doc sequence per organization"

setting reset document type:
1. pada ad_table, isilah field Document Date
2. pada window document sequence, centang field enable enable reset document type. kemudian isi field reset type dengan bulanan/tahunan.
3. masih pada window document sequence, klik tombol generate month and year, masukkan fiskal year, klik ok. maka akan terbentuk lines sesuai dengan period pad fiskal year yang dipilih. misalnya dalam fiskal year itu ada 12 periode (12 bulan), maka lines yang terbentuk akan ada 12 baris. lines ini akan memiliki nilai starting no dan next assigned no sesuai pada tab header.
4. untuk mencoba, bukalah salah satu window transaksi, misalnya purchase order, lalu klik new. maka pada pada field document no akan terbentuk nomor document sesuai dengan next assigned no. pada saat ini belum ada prefix, suffix, year, atau month. klik save pada document tersebut, maka field document no akan memiliki nilai dengan pattern: @prefix@@tahun@/@bulan@/@nextAssignedNumber@@suffix@.

::penjelasan setting yang diperlukan pada reset document type:
pada ad_table, ada kolom em_oez_documentdate, field ini berisi nama properti dari kolom yang berisi tanggal document. kolom ini digunakan oleh org.openbravo.client.application.event.SetDocumentNoHandler pada method private void handleEvent(EntityPersistenceEvent event) untuk me-look-up apa nama entiti yang menyimpan tanggal dokumen. selanjutnya tanggal dokumen akan dipakai dalam where clause dalam mencari periode. period tersebut selanjutnya akan diumpankan ke Utility.getDocNoSeqLine, yang akan me-return document no. didalam method ini terdapat prosedur string building yang memembentuk document no. jika ternyata em_oez_documentdate null, maka class SetDocumentNoHandler akan mengeksekusi business logic standard bawaan openbravo.

::kekurangan pada reset document type:
1. pada SetDocumentNoHandler, organisasi yang dipakai pada where clause dalam mencari periode, menggunakan organisasi pada dokumen transaksi, seharusnya organisasi yang dipakai adalah organisasi calendar owner. hal ini akan diselesaikan dengan memanfaatkan stored procedure ad_org_getcalendarowner pada saat penentuan organisasi pada where clause pencari periode dokumen.
pada method nextDocType di org.openbravo.erpCommon.utility.DocNoSeqLine, terdapat statement objectCSResponse.razon = prefix+Tahun+"/"+month+"/"+nextNumber+suffix; statement tersebut menjadikan patterd doc sequence ini selalu @prefix@@tahun@/@bulan@/@nextAssignedNumber@@suffix@. seharusnya diganti dengan regular expression
ada field "enable reset document sequence" pada window document sequence, tetapi tidak dipakai pada business logic. seharusnya pada method .. di .. dibagian statement if(propDate!=null){ ditambahkan juga: or enable reset document sequence = false. sebab kalau field ini nilainya false, artinya ikut document sequence bawaan openbravo.
field reset type di window document sequence diberi display logic jika = Y

::note
1. import sales order and import purchase order will work only if only:
1.1 make sure you have unique document type in client level. for example, pre-installed client FnB international have 2 organizations and each of them have standard order. this make import sales order and import purchase order failed due to subquery in java code return more than one row.
1.2 edit constant record in import loader format line, both currency ID and warehouse ID.

::note untuk document routing
DocumentRoutingHandler.execute --> DocumentRoutingHandlerServer.doRouting
DefaultRoutingHandlerAction >> default do nothing
DocumentRoutingHandlerAction >> template document routing concret class, semua doc routing config harus extend ke class ini 

further testing:
di invoice ada proses closing, pelajari apa dampaknya pada uang muka, lalu terapkan ke doc routing
di order ada proses closing, pelajari apa dampaknya pada uang muka, lalu terapkan ke doc routing
