package com.example.bookwander.fragments

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.bookwander.BookWanderApp
import com.example.bookwander.R
import com.example.bookwander.databinding.FragmentInsertBookBinding
import com.example.bookwander.module.Book
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.firebase.firestore.FirebaseFirestore
import java.text.ParseException

class InsertBookFragment: Fragment() {
    private lateinit var binding: FragmentInsertBookBinding
    private lateinit var app: BookWanderApp
    private val db: FirebaseFirestore
        get() = BookWanderApp.db

    private var selectedBookUuid: String? = null

    companion object {
        private const val ARG_SELECTED_BOOK_UUID = "selectedBookUuid"

        fun newInstance(selectedBookUuid: String): InsertBookFragment {
            val fragment = InsertBookFragment()
            val args = Bundle()
            args.putString(ARG_SELECTED_BOOK_UUID, selectedBookUuid)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentInsertBookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        app = requireActivity().application as BookWanderApp

        binding.bookNameInput.setEndIconOnClickListener(View.OnClickListener {
            binding.bookNameInput.editText?.text = null
        })

        binding.dateInput.setStartIconOnClickListener {
            val currentText = binding.dateInput.editText?.text.toString()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            val currentDate = try {
                dateFormat.parse(currentText)
            } catch (e: ParseException) {
                null
            }

            val datePicker =
                MaterialDatePicker.Builder.datePicker()
                    .setSelection(currentDate?.time ?: MaterialDatePicker.todayInUtcMilliseconds())
                    .setTitleText("Select date")
                    .setTheme(R.style.DatePickerStyle)
                    .build()

            datePicker.addOnPositiveButtonClickListener { selectedDate ->
                val formattedDate = dateFormat.format(Date(selectedDate))
                binding.dateInput.editText?.setText(formattedDate)
            }

            datePicker.show(requireActivity().supportFragmentManager, datePicker.toString())
        }

        binding.openClock.setOnClickListener {
            val currentTime = System.currentTimeMillis()

            val timePicker =
                MaterialTimePicker.Builder()
                    .setHour(12)
                    .setMinute(0)
                    .setTitleText("Select time")
                    .build()


            timePicker.show(requireActivity().supportFragmentManager, timePicker.toString())
        }

        binding.saveButton.setOnClickListener {
            onSaveButtonClick()
        }

        selectedBookUuid = arguments?.getString(ARG_SELECTED_BOOK_UUID)
        Log.d("InsertBookFragment", "Selected Book UUID: $selectedBookUuid")
        selectedBookUuid?.let { retrieveBookFromFirestore(it) }
    }

    private fun retrieveBookFromFirestore(selectedBookUuid: String) {
        db.collection("wishlist")
            .whereEqualTo("uuid", selectedBookUuid)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val book = document.toObject(Book::class.java)
                    Log.d("InsertBookFragment", "Retrieved Book: ${book.name}, UUID: ${book.uuid}")
                    populateViews(book)
                    break
                }
            }
            .addOnFailureListener { e ->
                Log.e("RetrieveFirestore", "Error retrieving document with UUID $selectedBookUuid: $e")
            }
    }

    private fun populateViews(book: Book) {
        binding.bookNameInput.editText?.setText(book.name)
        binding.authorInput.editText?.setText(book.author)
        binding.priceInput.editText?.setText(book.price.toString())
    }

    private fun onSaveButtonClick() {
        val bookName = binding.bookNameInput.editText?.text.toString()
        val author = binding.authorInput.editText?.text.toString()
        val price = binding.priceInput.editText?.text.toString().toDoubleOrNull() ?: 0.0

        val newBook = selectedBookUuid?.let {
            Book(name = bookName, author = author, price = price, uuid = it)
        } ?:
        Book(name = bookName, author = author, price = price)

        val existingBookIndex = app.wishList.indexOfFirst { it.uuid == newBook.uuid }

        if (existingBookIndex != -1) {
            app.wishList[existingBookIndex] = newBook
        } else {
            app.wishList.add(newBook)
        }

        saveBookToFirestore(newBook)
        requireActivity().supportFragmentManager.popBackStack()
    }



    private fun saveBookToFirestore(book: Book) {
        val bookData = mapOf(
            "name" to book.name,
            "author" to book.author,
            "price" to book.price,
            "uuid" to book.uuid
        )

        val wishlistCollection = db.collection("wishlist")

        wishlistCollection
            .whereEqualTo("uuid", book.uuid)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    wishlistCollection
                        .add(bookData)
                        .addOnSuccessListener { documentReference ->
                            Log.d("Firestore", "DocumentSnapshot added with ID: ${documentReference.id}")
                            app.triggerGlobalCallback("wishlistUpdated")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Error adding document: $e")
                        }
                } else {
                    wishlistCollection
                        .document(book.uuid)
                        .set(bookData)
                        .addOnSuccessListener {
                            Log.d("Firestore", "DocumentSnapshot updated with ID: ${book.uuid}")
                            app.triggerGlobalCallback("wishlistUpdated")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Error updating document with ID ${book.uuid}: $e")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error checking for existing document: $e")
            }
    }



}