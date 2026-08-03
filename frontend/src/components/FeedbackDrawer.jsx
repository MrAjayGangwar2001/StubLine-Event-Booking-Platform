import {
    Drawer,
    Box,
    Typography,
    TextField,
    Button,
    ThemeProvider,
    createTheme,
    IconButton,
} from '@mui/material'
import { useState } from 'react'

// Scoped to just this drawer (not the whole app) so it doesn't affect any
// other component - matches the ink/gold/paper palette from
// tailwind.config.js so this doesn't look like a bolted-on default-MUI form.
const darkTheme = createTheme({
    palette: {
        mode: 'dark',
        primary: { main: '#E8A33D' },      // gold
        background: { paper: '#12121A', default: '#12121A' }, // ink
        text: { primary: '#F1EFEA', secondary: '#8B8A99' },   // paper / paper-muted
    },
    shape: { borderRadius: 10 },
})

function FeedbackDrawer({ open, onClose }) {
    const [form, setForm] = useState({
        name: "",
        email: "",
        feedback: "",
    });
    const [status, setStatus] = useState(""); // Success or error message

    const handleChange = (e) => {
        const { name, value } = e.target;
        setForm({
            ...form,
            [name]: value,
        });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.feedback.trim()) return alert('Please enter feedback')

        setStatus("Sending...");

        const formData = new FormData();
        // Set VITE_WEB3FORMS_ACCESS_KEY in frontend/.env - see README note.
        formData.append("access_key", import.meta.env.VITE_WEB3FORMS_ACCESS_KEY);
        formData.append("subject", "New StubLine Feedback");
        formData.append("name", form.name);
        formData.append("email", form.email);
        formData.append("feedback", form.feedback);

        try {
            const response = await fetch("https://api.web3forms.com/submit", {
                method: "POST",
                body: formData,
            });

            const result = await response.json();

            if (result.success) {
                setStatus("Message sent successfully!");
                alert('Thank you for your feedback 🙏')
                setForm({ name: "", email: "", feedback: "" });
                onClose()
            } else {
                setStatus("Something went wrong. Try again later.");
            }
        } catch (error) {
            setStatus("Error sending message.");
        }
    };

    return (
        <ThemeProvider theme={darkTheme}>
            <Drawer
                anchor="bottom"
                open={open}
                onClose={onClose}
                PaperProps={{
                    sx: {
                        backgroundColor: '#12121A',
                        backgroundImage: 'none',
                        borderTop: '1px solid #E8A33D',
                        borderTopLeftRadius: 16,
                        borderTopRightRadius: 16,
                    },
                }}
            >
                <Box sx={{ p: 3, maxWidth: 600, mx: 'auto', width: '100%' }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
                        <Typography variant="h6" sx={{ color: '#E8A33D', fontWeight: 700 }}>
                            Send Feedback
                        </Typography>
                        <IconButton onClick={onClose} size="small" sx={{ color: '#8B8A99' }} aria-label="Close">
                            ✕
                        </IconButton>
                    </Box>

                    <form onSubmit={handleSubmit}>
                        <TextField
                            fullWidth
                            name="name"
                            label="Your Name"
                            value={form.name}
                            required
                            onChange={handleChange}
                            sx={{ mb: 2 }}
                        />
                        <TextField
                            fullWidth
                            name="email"
                            type='email'
                            label="Your email"
                            value={form.email}
                            required
                            onChange={handleChange}
                            sx={{ mb: 2 }}
                        />

                        <TextField
                            fullWidth
                            multiline
                            name="feedback"
                            rows={4}
                            label="Your Feedback"
                            value={form.feedback}
                            onChange={handleChange}
                        />

                        <Button
                            type="submit"
                            variant="contained"
                            fullWidth
                            sx={{ mt: 2, color: '#12121A', fontWeight: 700 }}
                        >
                            Submit Feedback
                        </Button>
                    </form>
                    {status && (
                        <div className="mt-3 text-center text-sm" style={{ color: '#8B8A99' }}>{status}</div>
                    )}
                </Box>
            </Drawer>
        </ThemeProvider>
    )
}

export default FeedbackDrawer
